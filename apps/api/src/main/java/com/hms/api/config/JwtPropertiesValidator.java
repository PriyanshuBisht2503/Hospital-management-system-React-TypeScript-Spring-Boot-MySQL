package com.hms.api.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtPropertiesValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtPropertiesValidator.class);

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @PostConstruct
    public void validate() {
        if (jwtSecret == null || jwtSecret.trim().length() < 32) {
            String errorMsg = "FATAL: JWT_SECRET must be at least 32 characters (256 bits) long for secure HS256/HS384/HS512 signing.";
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        log.info("JWT secret verified successfully (length: {} characters).", jwtSecret.length());
    }
}
