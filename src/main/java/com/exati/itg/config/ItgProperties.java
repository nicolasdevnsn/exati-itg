package com.exati.itg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Instance-level configuration, bound from the {@code itg.*} tree.
 *
 * <p>{@code itg.env} defaults to {@code dev} in {@code application.yml}
 * ({@code ${ITG_ENV:dev}}); an unknown value fails startup via Spring's enum
 * binding, and a missing value is rejected by {@code MirrorConfig} — deploys
 * must state their environment rather than silently inherit dev behavior.
 *
 * <p>{@code itg.dev.*} configures the dev-only SIP database access (SSH
 * tunnel + MySQL); it is only read when {@code itg.env=dev}.
 */
@ConfigurationProperties(prefix = "itg")
public record ItgProperties(
        ItgEnvironment env,
        Dev dev
) {

    /** Dev-environment infrastructure: how to reach the SIP database. */
    public record Dev(
            Access access,
            Ssh ssh,
            Db db,
            Recheck recheck
    ) {

        /**
         * Recheck job: re-reads non-terminal mirrored tickets from the Exati
         * listing and syncs the known fields. Tickets in a terminal status are
         * never re-checked.
         */
        public record Recheck(
                long periodMinutes,
                java.util.Set<String> terminalStatuses,
                long expireDays
        ) {
        }

        /**
         * Transport to the SIP database. {@code TUNNEL} = app-managed SSH
         * port-forward (running outside the SIP environment, today's case);
         * {@code DIRECT} = the database address is routable as-is (the app
         * deployed inside the SIP environment). {@code itg.dev.ssh.*} is only
         * read in {@code TUNNEL} mode.
         */
        public enum Access {
            TUNNEL,
            DIRECT
        }

        /**
         * SSH tunnel to the SIP VM. {@code keyPath} is a PEM private key file
         * (the dev VM key is PKCS#1 RSA); {@code localPort} is the local end
         * of the port-forward the MySQL datasource connects to.
         */
        public record Ssh(
                String host,
                int port,
                String user,
                String keyPath,
                int localPort
        ) {
        }

        /**
         * SIP MySQL as seen FROM the SSH VM ({@code host}/{@code port} are the
         * forward's remote end, not a locally reachable address).
         */
        public record Db(
                String host,
                int port,
                String schema,
                String username,
                String password
        ) {
        }
    }
}
