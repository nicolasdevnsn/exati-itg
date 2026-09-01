package com.exati.itg.mirror;

/**
 * How this instance reaches the SIP database — the transport seam.
 *
 * <p>The mirror code sees only a host/port to point JDBC at; whether the
 * bytes cross an app-managed SSH forward ({@link SshTunnelConnectivity}) or
 * go straight to the database ({@link DirectConnectivity}, for when the app
 * runs inside the SIP environment) is decided by configuration
 * ({@code itg.dev.access}) in {@link SipDevConfig} and nowhere else.
 *
 * <p>Implementations own their whole lifecycle including failure semantics:
 * being unreachable is reported via {@link #isUp()}, never thrown as fatal —
 * the mirror queues writes until connectivity returns.
 */
public interface SipDatabaseConnectivity extends AutoCloseable {

    /** Host the JDBC URL should target. Stable for the life of the app. */
    String host();

    /** Port the JDBC URL should target. Stable for the life of the app. */
    int port();

    /** Whether the transport is currently believed usable. */
    boolean isUp();

    @Override
    default void close() {
        // stateless implementations need no teardown
    }
}
