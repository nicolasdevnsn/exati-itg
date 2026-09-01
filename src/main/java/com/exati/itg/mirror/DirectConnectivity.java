package com.exati.itg.mirror;

import com.exati.itg.config.ItgProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Direct reachability: the SIP database address is routable from where the
 * app runs (the future topology — the app deployed inside the SIP
 * environment). No tunnel, no lifecycle; JDBC connects straight to the
 * configured host/port and failures surface on use like any network error.
 */
@Slf4j
public class DirectConnectivity implements SipDatabaseConnectivity {

    private final String host;
    private final int port;

    public DirectConnectivity(ItgProperties.Dev.Db db) {
        this.host = db.host();
        this.port = db.port();
        log.info("SIP database access: direct to {}:{}", host, port);
    }

    @Override
    public String host() {
        return host;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public boolean isUp() {
        return true;
    }
}
