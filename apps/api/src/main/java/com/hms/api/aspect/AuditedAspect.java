package com.hms.api.aspect;

import com.hms.api.annotation.Audited;
import com.hms.api.security.ClientIpResolver;
import com.hms.api.security.SecurityUser;
import com.hms.api.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aspect implementing @Audited logic.
 * Guarantees:
 * 1. Audit failures NEVER break the business request.
 * 2. Sensitive fields (passwords, tokens, OTP codes, credentials) are NEVER logged.
 */
@Aspect
@Component
public class AuditedAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditedAspect.class);

    private static final Set<String> SENSITIVE_KEYWORDS = new HashSet<>(Arrays.asList(
        "password", "token", "otp", "code", "secret", "credential", "auth", "hash"
    ));

    private final AuditLogService auditLogService;
    private final ClientIpResolver clientIpResolver;

    public AuditedAspect(AuditLogService auditLogService, ClientIpResolver clientIpResolver) {
        this.auditLogService = auditLogService;
        this.clientIpResolver = clientIpResolver;
    }

    @Around("@annotation(audited)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            recordAuditEntry(joinPoint, audited, result);
        } catch (Throwable t) {
            // NEVER break the main business execution if audit logging throws an exception
            log.error("[AuditedAspect] Non-fatal error recording audit for action {}: {}", audited.action(), t.getMessage());
        }

        return result;
    }

    private void recordAuditEntry(ProceedingJoinPoint joinPoint, Audited audited, Object result) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        // 1. Resolve User ID from SecurityContext
        Long userId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SecurityUser securityUser) {
            userId = securityUser.getId();
        }

        // 2. Resolve Client IP from current HTTP request
        String clientIp = "127.0.0.1";
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            clientIp = clientIpResolver.resolveClientIp(request);
        }

        // 3. Resolve Entity ID
        String entityId = resolveEntityId(audited.entityIdParam(), paramNames, args);

        // 4. Resolve Entity Type
        String entityType = audited.entityType();
        if (entityType.isBlank()) {
            entityType = method.getDeclaringClass().getSimpleName().replace("Controller", "").replace("Service", "").toUpperCase();
        }

        // 5. Build sanitized details string (guaranteeing no passwords, tokens, or OTP codes leak)
        String details = buildSanitizedDetails(method, paramNames, args);

        // 6. Persist through AuditLogService
        auditLogService.log(userId, audited.action(), entityType, entityId, clientIp, details);
    }

    private String resolveEntityId(String entityIdParam, String[] paramNames, Object[] args) {
        if (entityIdParam != null && !entityIdParam.isBlank() && paramNames != null && args != null) {
            for (int i = 0; i < paramNames.length; i++) {
                if (entityIdParam.equalsIgnoreCase(paramNames[i]) && args[i] != null) {
                    return String.valueOf(args[i]);
                }
            }
        }

        // Fallback: Check if first argument is a Long or String id
        if (args != null && args.length > 0 && args[0] != null) {
            if (args[0] instanceof Long || args[0] instanceof Integer) {
                return String.valueOf(args[0]);
            }
        }

        return "N/A";
    }

    private String buildSanitizedDetails(Method method, String[] paramNames, Object[] args) {
        if (paramNames == null || args == null || paramNames.length == 0) {
            return "Invoked " + method.getName();
        }

        StringBuilder sb = new StringBuilder("Method: ").append(method.getName()).append("(");
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < paramNames.length; i++) {
            if (i > 0) sb.append(", ");
            String paramName = paramNames[i];
            Object arg = args[i];

            if (isSensitiveParam(paramName, parameters[i].getType())) {
                sb.append(paramName).append("=[PROTECTED]");
            } else if (arg == null) {
                sb.append(paramName).append("=null");
            } else {
                String strVal = arg.toString();
                // Check if string content might contain sensitive keywords
                if (containsSensitiveContent(strVal)) {
                    sb.append(paramName).append("=[PROTECTED]");
                } else {
                    sb.append(paramName).append("=").append(truncate(strVal, 60));
                }
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private boolean isSensitiveParam(String name, Class<?> type) {
        String lowerName = name.toLowerCase();
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lowerName.contains(keyword)) {
                return true;
            }
        }

        String typeName = type.getSimpleName().toLowerCase();
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (typeName.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsSensitiveContent(String content) {
        String lower = content.toLowerCase();
        return lower.contains("password=") || lower.contains("rawcode=") || lower.contains("token=") || lower.contains("secret=");
    }

    private String truncate(String val, int max) {
        if (val == null) return null;
        return val.length() > max ? val.substring(0, max) + "..." : val;
    }
}
