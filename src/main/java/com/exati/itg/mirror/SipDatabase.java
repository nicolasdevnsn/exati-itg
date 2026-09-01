package com.exati.itg.mirror;

import com.exati.itg.config.ItgProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The dev-only connection to the SIP MySQL, at whatever endpoint the active
 * {@link SipDatabaseConnectivity} provides (SSH forward or direct).
 *
 * <p>Deliberately NOT a Spring {@code DataSource} bean: exposing one would
 * make Boot's datasource auto-configuration back off and kill the primary H2.
 * The pool lives privately here and the mirror code sees only the
 * {@link JdbcClient} — plain SQL, no JPA, no entanglement with the primary
 * persistence unit.
 *
 * <p>The pool starts lazily and tolerates the endpoint being unreachable
 * ({@code initializationFailTimeout = -1}): connection failures surface on
 * use, where the mirror queues and retries.
 */
public class SipDatabase implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final JdbcClient jdbc;

    public SipDatabase(ItgProperties.Dev.Db db, SipDatabaseConnectivity connectivity) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("sip-mirror");
        config.setJdbcUrl("jdbc:mysql://%s:%d/%s".formatted(
                connectivity.host(), connectivity.port(), db.schema())
                + "?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
        config.setUsername(db.username());
        config.setPassword(db.password());
        // Small pool: the mirror is a trickle of writes plus the query endpoint.
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(10_000);
        config.setInitializationFailTimeout(-1);
        this.dataSource = new HikariDataSource(config);
        this.jdbc = JdbcClient.create(dataSource);
    }

    public JdbcClient jdbc() {
        return jdbc;
    }

    /** Round-trip probe; throws when the tunnel or the database is unreachable. */
    public void ping() {
        jdbc.sql("SELECT 1").query(Integer.class).single();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
