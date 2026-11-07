package com.hms.api.dto;

import com.hms.api.domain.AuditLog;
import java.time.LocalDateTime;

public record AuditLogDto(
    Long id,
    Long userId,
    String action,
    String entityType,
    String entityId,
    String ipAddress,
    String details,
    LocalDateTime createdAt
) {
    public static AuditLogDto from(AuditLog log) {
        return new AuditLogDto(
            log.getId(),
            log.getUserId(),
            log.getAction(),
            log.getEntityType(),
            log.getEntityId(),
            log.getIp(),
            log.getDetails(),
            log.getCreatedAt()
        );
    }
}
