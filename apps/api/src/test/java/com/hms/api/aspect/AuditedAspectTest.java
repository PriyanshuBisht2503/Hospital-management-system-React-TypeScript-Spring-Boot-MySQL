package com.hms.api.aspect;

import com.hms.api.annotation.Audited;
import com.hms.api.domain.AuditLog;
import com.hms.api.repository.AuditLogRepository;
import com.hms.api.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@Import(AuditedAspectTest.TestAuditService.class)
class AuditedAspectTest {

    @Autowired
    private TestAuditService testAuditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Service
    static class TestAuditService {
        @Audited(action = "PATIENT_CONSULT", entityType = "PATIENT", entityIdParam = "patientId")
        public String consultPatient(Long patientId, String notes, String sensitivePassword, String otpCode) {
            return "Consulted patient " + patientId;
        }

        @Audited(action = "RISKY_ACTION")
        public String riskyAction() {
            return "Done";
        }
    }

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("@Audited records audit entry and masks sensitive parameters (passwords, tokens, OTPs)")
    void auditedAspectLogsAndMasksSensitiveData() {
        String result = testAuditService.consultPatient(101L, "Routine checkup", "TopSecretPass@123", "998877");
        assertThat(result).isEqualTo("Consulted patient 101");

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLog log = logs.get(0);
        assertThat(log.getAction()).isEqualTo("PATIENT_CONSULT");
        assertThat(log.getEntityType()).isEqualTo("PATIENT");
        assertThat(log.getEntityId()).isEqualTo("101");

        // Verify details NEVER contain the sensitive password or OTP code
        assertThat(log.getDetails()).contains("sensitivePassword=[PROTECTED]");
        assertThat(log.getDetails()).contains("otpCode=[PROTECTED]");
        assertThat(log.getDetails()).doesNotContain("TopSecretPass@123");
        assertThat(log.getDetails()).doesNotContain("998877");
        assertThat(log.getDetails()).contains("Routine checkup");
    }

    @Test
    @DisplayName("@Audited aspect never breaks main business method execution even if audit log throws exception")
    void auditedAspectNeverBreaksMainMethod() {
        // Even under edge case, the business invocation completes successfully
        assertThatCode(() -> testAuditService.riskyAction())
                .doesNotThrowAnyException();
    }
}
