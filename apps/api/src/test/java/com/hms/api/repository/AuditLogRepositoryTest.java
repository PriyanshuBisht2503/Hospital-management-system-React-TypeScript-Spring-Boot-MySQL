package com.hms.api.repository;

import com.hms.api.domain.AuditLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("Should persist and search audit logs by action and entity type")
    void shouldPersistAndSearchAuditLogs() {
        AuditLog log1 = new AuditLog(10L, "USER_LOGIN", "USER", "10", "127.0.0.1", "Successful login");
        AuditLog log2 = new AuditLog(10L, "ROLE_CHANGE", "USER", "15", "127.0.0.1", "Changed role to DOCTOR");
        AuditLog log3 = new AuditLog(20L, "BRANCH_CREATE", "BRANCH", "1", "192.168.1.1", "Created branch Mankhool");

        auditLogRepository.save(log1);
        auditLogRepository.save(log2);
        auditLogRepository.save(log3);

        Page<AuditLog> userLogs = auditLogRepository.searchAuditLogs(10L, null, null, null, null, PageRequest.of(0, 10));
        assertThat(userLogs.getContent()).hasSize(2);

        Page<AuditLog> branchLogs = auditLogRepository.searchAuditLogs(null, "BRANCH_CREATE", "BRANCH", null, null, PageRequest.of(0, 10));
        assertThat(branchLogs.getContent()).hasSize(1);
        assertThat(branchLogs.getContent().get(0).getEntityId()).isEqualTo("1");
    }
}
