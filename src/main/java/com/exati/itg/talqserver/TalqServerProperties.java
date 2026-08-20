package com.exati.itg.talqserver;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identity and announced limits of this TALQ gateway. The values MUST mirror
 * what was announced to the CMS during bootstrap (homolog kit under
 * {@code homolog/}) — the certifier compares both sides.
 */
@ConfigurationProperties(prefix = "talq.server")
public record TalqServerProperties(
        String gatewayAddress,
        String cmsAddress,
        String gatewayUri,
        String cmsUri,
        String vendor,
        String crlUrn,
        Limits limits
) {
    public record Limits(
            int maximumCalendars,
            int maximumPrograms,
            int maxProgramsPerCalendar,
            int maxSwitchPointsPerProgram,
            int maxActivePeriodsPerProgram,
            int maximumNumberOfGroups,
            int maximumGroupSize,
            int maximumDataLogs
    ) {
    }
}
