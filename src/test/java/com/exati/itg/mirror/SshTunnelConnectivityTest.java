package com.exati.itg.mirror;

import com.exati.itg.config.ItgProperties;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.AcceptAllPublickeyAuthenticator;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real tunnel path against an embedded MINA SSH server and a
 * local echo target: PKCS#1 PEM key loading (the dev VM key's format),
 * authentication, local port-forwarding, and graceful failure when the SSH
 * endpoint is unreachable.
 */
class SshTunnelConnectivityTest {

    @TempDir
    static Path tempDir;

    private static SshServer sshd;
    private static ServerSocket echoServer;
    private static Thread echoThread;
    private static Path keyPath;

    @BeforeAll
    static void startInfrastructure() throws Exception {
        // Client key in PKCS#1 PEM, like the real hong_baiyi.pem.
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair clientKey = gen.generateKeyPair();
        keyPath = tempDir.resolve("test-client.pem");
        try (JcaPEMWriter pem = new JcaPEMWriter(new FileWriter(keyPath.toFile()))) {
            pem.writeObject(clientKey);
        }

        echoServer = new ServerSocket(0);
        echoThread = new Thread(() -> {
            try {
                while (!echoServer.isClosed()) {
                    Socket s = echoServer.accept();
                    new Thread(() -> {
                        try (s; InputStream in = s.getInputStream();
                             OutputStream out = s.getOutputStream()) {
                            in.transferTo(out);
                        } catch (Exception ignored) {
                        }
                    }).start();
                }
            } catch (Exception ignored) {
            }
        }, "echo-server");
        echoThread.setDaemon(true);
        echoThread.start();

        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(0);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(tempDir.resolve("hostkey.ser")));
        sshd.setPublickeyAuthenticator(AcceptAllPublickeyAuthenticator.INSTANCE);
        sshd.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
        sshd.start();
    }

    @AfterAll
    static void stopInfrastructure() throws Exception {
        if (sshd != null) {
            sshd.stop(true);
        }
        if (echoServer != null) {
            echoServer.close();
        }
    }

    private static ItgProperties.Dev devProps(String sshHost, int sshPort, int localPort) {
        return new ItgProperties.Dev(
                ItgProperties.Dev.Access.TUNNEL,
                new ItgProperties.Dev.Ssh(sshHost, sshPort, "test",
                        keyPath.toString(), localPort),
                new ItgProperties.Dev.Db("127.0.0.1", echoServer.getLocalPort(),
                        "ami", "ami", ""));
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    void forwardsLocalPortThroughTheTunnel() throws Exception {
        int localPort = freePort();
        try (SshTunnelConnectivity tunnel = new SshTunnelConnectivity(
                devProps("127.0.0.1", sshd.getPort(), localPort))) {

            // The constructor's watchdog fires immediately; wait for it.
            long deadline = System.currentTimeMillis() + 15_000;
            while (!tunnel.isUp() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            assertThat(tunnel.isUp()).as("tunnel established").isTrue();

            try (Socket socket = new Socket("127.0.0.1", localPort)) {
                byte[] payload = "SELECT 1".getBytes(StandardCharsets.US_ASCII);
                socket.getOutputStream().write(payload);
                socket.getOutputStream().flush();
                byte[] reply = socket.getInputStream().readNBytes(payload.length);
                assertThat(reply).as("echo through the forwarded port").isEqualTo(payload);
            }
        }
    }

    @Test
    void unreachableSshEndpoint_failsSoftly() throws Exception {
        int localPort = freePort();
        // Nothing listens on the target port: ensureUp must swallow the
        // failure (tunnel down != app down) and simply report not-up.
        try (SshTunnelConnectivity tunnel = new SshTunnelConnectivity(
                devProps("127.0.0.1", freePort(), localPort))) {
            tunnel.ensureUp();
            assertThat(tunnel.isUp()).isFalse();
        }
    }
}
