package com.hms.api.service;

import com.hms.api.domain.AuditLog;
import com.hms.api.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Writes an audit entry. Runs in a REQUIRES_NEW transaction so that
     * audit entries persist even if the calling transaction rolls back (e.g. failed logins).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String entityType, String entityId, String ip, String details) {
        try {
            AuditLog auditLog = new AuditLog(userId, action, entityType, entityId, ip, details);
            auditLogRepository.save(auditLog);
            log.info("[AUDIT] action={}, entityType={}, entityId={}, userId={}, ip={}",
                    action, entityType, entityId, userId, ip);
        } catch (Exception e) {
            log.error("Failed to write audit log entry: {}", e.getMessage(), e);
        }
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
