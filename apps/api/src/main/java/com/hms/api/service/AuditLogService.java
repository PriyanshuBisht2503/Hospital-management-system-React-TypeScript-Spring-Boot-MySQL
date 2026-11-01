package com.hms.api.service;

import com.hms.api.domain.AuditLog;
import com.hms.api.repository.AuditLogRepository;
import com.hms.api.security.ClientIpResolver;
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
    private final ClientIpResolver clientIpResolver;

    public AuditLogService(AuditLogRepository auditLogRepository, ClientIpResolver clientIpResolver) {
        this.auditLogRepository = auditLogRepository;
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * Writes an audit entry. Runs in a REQUIRES_NEW transaction so that
     * audit entries persist even if the calling transaction rolls back (e.g. failed logins).
     * Truncates entityId to prevent exceeding column bounds (e.g. from long attempted emails).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String entityType, String entityId, String ip, String details) {
        try {
            String safeAction = truncate(action, 50);
            String safeEntityType = truncate(entityType, 50);
            String safeEntityId = truncate(entityId, 50);
            String safeIp = truncate(ip, 45);

            AuditLog auditLog = new AuditLog(userId, safeAction, safeEntityType, safeEntityId, safeIp, details);
            auditLogRepository.save(auditLog);
            log.info("[AUDIT] action={}, entityType={}, entityId={}, userId={}, ip={}",
                    safeAction, safeEntityType, safeEntityId, userId, safeIp);
        } catch (Exception e) {
            log.error("Failed to write audit log entry: {}", e.getMessage(), e);
        }
    }

    public String resolveClientIp(HttpServletRequest request) {
        return clientIpResolver.resolveClientIp(request);
    }

    public com.hms.api.dto.PageResponse<com.hms.api.dto.AuditLogDto> searchLogs(
            Long userId,
            String action,
            String entityType,
            java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate,
            org.springframework.data.domain.Pageable pageable
    ) {
        org.springframework.data.domain.Page<AuditLog> page = auditLogRepository.searchAuditLogs(userId, action, entityType, startDate, endDate, pageable);
        return com.hms.api.dto.PageResponse.from(page.map(com.hms.api.dto.AuditLogDto::from));
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
