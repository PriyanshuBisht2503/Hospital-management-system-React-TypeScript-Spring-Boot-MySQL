package com.hms.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a controller or service method execution should be automatically
 * recorded to the audit trail.
 * The aspect guarantees that audit logging failures will never break the main request
 * and that sensitive values (passwords, tokens, OTP codes) are never logged.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /**
     * The action identifier to record in the audit log (e.g. "USER_CREATED", "PROFILE_UPDATED").
     */
    String action();

    /**
     * The entity type being acted upon (e.g. "USER", "DOCTOR", "BRANCH"). Defaults to empty string.
     */
    String entityType() default "";

    /**
     * Optional method parameter name to extract as the entity ID.
     */
    String entityIdParam() default "";
}
