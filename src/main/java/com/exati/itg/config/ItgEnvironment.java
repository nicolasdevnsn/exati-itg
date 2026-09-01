package com.exati.itg.config;

/**
 * Deployment environment of this gateway instance, selected by the
 * {@code ITG_ENV} environment variable ({@code itg.env} property).
 *
 * <p>The value gates environment-specific infrastructure — today the SIP
 * ticket mirror, which only {@link #DEV} implements (SSH tunnel to the SIP
 * MySQL). Other environments get an explicit no-op: they must NEVER fall back
 * to the dev tunnel. Adding an environment = new constant here + its own
 * beans where behavior differs.
 */
public enum ItgEnvironment {
    /** Exati dev/sandbox (the certifier). SIP ticket mirror active via SSH tunnel. */
    DEV,
    /** Quality assurance. No mirror implementation yet — no-op. */
    QA,
    /** Production. No mirror implementation yet — no-op. */
    PROD
}
