package com.hms.api.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Fails application startup when the JWT signing secret is missing, too short, or a
 * known development default outside the local/test profiles.
 */
@Component
public class JwtPropertiesValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtPropertiesValidator.class);

    static final int MIN_SECRET_LENGTH = 32;

    /** The development default secret. It may be used only by the dev/test profiles. */
    static final String DEV_DEFAULT_SECRET = "supersecretjwtkeythatmustbechangedinproductionandhasatleast256bitslength";

    /** Known insecure/default values that must never sign production tokens. */
    private static final Set<String> KNOWN_DEV_DEFAULT_SECRETS = Set.of(DEV_DEFAULT_SECRET);

    /** Profiles where the dev default secret is permitted (local development + hermetic tests). */
    private static final Set<String> PROFILES_ALLOWING_DEV_DEFAULT = Set.of("dev", "test", "mysql-test");

    private final Environment environment;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    public JwtPropertiesValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            fail("FATAL: JWT_SECRET must be set. Configure a strong secret of at least "
                    + MIN_SECRET_LENGTH + " characters (256 bits).");
        }

        if (jwtSecret.trim().length() < MIN_SECRET_LENGTH) {
            fail("FATAL: JWT_SECRET must be at least " + MIN_SECRET_LENGTH
                    + " characters (256 bits) long for secure HS256/HS384/HS512 signing.");
        }

        if (!devOrTestProfileActive() && KNOWN_DEV_DEFAULT_SECRETS.contains(jwtSecret.trim())) {
            fail("FATAL: JWT_SECRET is set to a known development default value. "
                    + "Set a unique, strong production secret (at least " + MIN_SECRET_LENGTH + " characters).");
        }

        log.info("JWT secret verified successfully (length: {} characters).", jwtSecret.length());
    }

    private boolean devOrTestProfileActive() {
        if (environment == null) {
            return false;
        }
        return Arrays.stream(environment.getActiveProfiles()).anyMatch(PROFILES_ALLOWING_DEV_DEFAULT::contains);
    }

    private void fail(String message) {
        log.error(message);
        throw new IllegalStateException(message);
    }
}
