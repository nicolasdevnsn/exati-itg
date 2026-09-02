package com.exati.itg.mirror;

import com.exati.itg.config.ItgProperties;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Set;

/**
 * Shared access to the REAL SIP MySQL for the mirror tests — the test
 * environment's own database, same {@code exati_itg_ticket} table the app
 * writes to (no separate schema, no reserved id ranges); each test cleans up
 * the rows it creates.
 *
 * <p>Opens its own SSH tunnel on a free local port, so it never collides with
 * a running app. Requires the database password in {@code ITG_DEV_DB_PASSWORD}
 * (or the {@code itg.dev.db.password} system property); without it — or with
 * the VM unreachable — the tests are skipped rather than failing the build.
 */
final class SipTestDatabase {

    private static final String SSH_HOST = env("ITG_DEV_SSH_HOST", "3.88.22.232");
    private static final String SSH_USER = env("ITG_DEV_SSH_USER", "hong_baiyi");
    private static final String SSH_KEY = env("ITG_DEV_SSH_KEY", "../EXATI/hong_baiyi.pem");
    private static final String DB_HOST = env("ITG_DEV_DB_HOST", "34.232.210.135");
    private static final String DB_SCHEMA = env("ITG_DEV_DB_SCHEMA", "ami");
    private static final String DB_USER = env("ITG_DEV_DB_USER", "ami");

    private static SshTunnelConnectivity tunnel;
    private static SipDatabase database;

    private SipTestDatabase() {
    }

    /** The live database, or a skipped test when the environment isn't reachable. */
    static synchronized SipDatabase get() {
        String password = password();
        Assumptions.assumeTrue(password != null && !password.isBlank(),
                "SIP mirror tests need the database password in ITG_DEV_DB_PASSWORD");
        if (database == null) {
            ItgProperties.Dev dev = new ItgProperties.Dev(
                    ItgProperties.Dev.Access.TUNNEL,
                    new ItgProperties.Dev.Ssh(SSH_HOST, 22, SSH_USER, SSH_KEY, freePort()),
                    new ItgProperties.Dev.Db(DB_HOST, 3306, DB_SCHEMA, DB_USER, password),
                    new ItgProperties.Dev.Recheck(15, Set.of("RESOLVED", "CANCELED"), 60));
            SshTunnelConnectivity t = new SshTunnelConnectivity(dev);
            awaitTunnel(t);
            tunnel = t;
            database = new SipDatabase(dev.db(), t);
            Runtime.getRuntime().addShutdownHook(new Thread(SipTestDatabase::closeQuietly));
        }
        return database;
    }

    /** Remove the rows a test created, by protocol id. */
    static void cleanUp(long... externalProtocols) {
        if (database == null) {
            return;
        }
        for (long protocol : externalProtocols) {
            try {
                database.jdbc()
                        .sql("DELETE FROM exati_itg_ticket WHERE id_external_protocol = ?")
                        .param(protocol).update();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    private static void awaitTunnel(SshTunnelConnectivity t) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!t.isUp() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Assumptions.assumeTrue(t.isUp(),
                "SIP mirror tests need the SSH tunnel to " + SSH_HOST + " (VM unreachable?)");
    }

    private static String password() {
        String fromEnv = System.getenv("ITG_DEV_DB_PASSWORD");
        return fromEnv != null ? fromEnv : System.getProperty("itg.dev.db.password");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value != null && !value.isBlank() ? value : fallback;
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("No free local port for the test tunnel", e);
        }
    }

    private static void closeQuietly() {
        if (database != null) {
            database.close();
        }
        if (tunnel != null) {
            tunnel.close();
        }
    }
}
