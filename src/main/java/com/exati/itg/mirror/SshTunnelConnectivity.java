package com.exati.itg.mirror;

import com.exati.itg.config.ItgProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.common.util.security.SecurityUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * App-managed SSH tunnel to the SIP environment (dev only): keeps a local
 * port-forward open from {@code 127.0.0.1:<localPort>} to the SIP MySQL as
 * seen from the SSH VM. The mirror datasource connects to the local end.
 *
 * <p>Self-healing, never fatal: a watchdog re-establishes the tunnel with a
 * fixed cadence, and while it is down the mirror simply queues writes — the
 * tunnel being unreachable must never take the app down.
 */
@Slf4j
public class SshTunnelConnectivity implements SipDatabaseConnectivity {

    private static final Duration SSH_TIMEOUT = Duration.ofSeconds(15);
    private static final long WATCHDOG_PERIOD_SECONDS = 15;
    /** After the first failure, only every Nth is logged at WARN. */
    private static final int LOG_EVERY_N_FAILURES = 20;

    private final ItgProperties.Dev.Ssh ssh;
    private final SshdSocketAddress remote;
    private final SshClient client;
    private final ScheduledExecutorService watchdog;

    private volatile ClientSession session;
    private int consecutiveFailures;

    public SshTunnelConnectivity(ItgProperties.Dev dev) {
        this.ssh = dev.ssh();
        this.remote = new SshdSocketAddress(dev.db().host(), dev.db().port());
        this.client = SshClient.setUpDefaultClient();
        // Dev VM only; its host key is not distributed, so pinning would just
        // break the first run on every machine.
        this.client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        this.client.start();
        this.watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sip-ssh-tunnel");
            t.setDaemon(true);
            return t;
        });
        this.watchdog.scheduleWithFixedDelay(this::ensureUp,
                0, WATCHDOG_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    /** JDBC targets the local end of the forward. */
    @Override
    public String host() {
        return "127.0.0.1";
    }

    @Override
    public int port() {
        return ssh.localPort();
    }

    @Override
    public boolean isUp() {
        ClientSession s = session;
        return s != null && s.isOpen();
    }

    /**
     * Connect and forward if not already up. Synchronized: only the watchdog
     * and tests call this, and a reconnect must never race itself.
     */
    public synchronized void ensureUp() {
        if (isUp()) {
            return;
        }
        try {
            ClientSession s = client.connect(ssh.user(), ssh.host(), ssh.port())
                    .verify(SSH_TIMEOUT)
                    .getSession();
            for (KeyPair kp : loadKeys()) {
                s.addPublicKeyIdentity(kp);
            }
            s.auth().verify(SSH_TIMEOUT);
            s.startLocalPortForwarding(
                    new SshdSocketAddress(SshdSocketAddress.LOCALHOST_IPV4, ssh.localPort()),
                    remote);
            session = s;
            consecutiveFailures = 0;
            log.info("SIP SSH tunnel up: 127.0.0.1:{} -> {}:{} (via {}@{})",
                    ssh.localPort(), remote.getHostName(), remote.getPort(),
                    ssh.user(), ssh.host());
        } catch (Exception e) {
            consecutiveFailures++;
            if (consecutiveFailures == 1 || consecutiveFailures % LOG_EVERY_N_FAILURES == 0) {
                log.warn("SIP SSH tunnel unavailable (attempt {}): {} — mirror writes queue until it returns",
                        consecutiveFailures, e.getMessage());
            } else {
                log.debug("SIP SSH tunnel still unavailable (attempt {})", consecutiveFailures, e);
            }
        }
    }

    private Iterable<KeyPair> loadKeys() throws Exception {
        Path key = Path.of(ssh.keyPath());
        try (InputStream in = Files.newInputStream(key)) {
            return SecurityUtils.loadKeyPairIdentities(null,
                    () -> key.toAbsolutePath().toString(), in, null);
        }
    }

    @Override
    public void close() {
        watchdog.shutdownNow();
        ClientSession s = session;
        if (s != null) {
            try {
                s.close();
            } catch (Exception e) {
                log.debug("Error closing SSH session", e);
            }
        }
        client.stop();
    }
}
