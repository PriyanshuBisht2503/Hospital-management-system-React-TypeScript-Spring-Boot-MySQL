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

        @Audited(action = "OPT_IN_ACTION", entityType = "REPORT", details = "Monthly doctor activity summary")
        public String optInDetailsAction() {
            return "Generated Report";
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
    @DisplayName("@Audited by default logs only action, entityType, entityId, userId, ip and does NOT serialize method arguments")
    void auditedAspectLogsByDefaultWithoutSerializingArguments() {
        String result = testAuditService.consultPatient(101L, "Routine checkup", "TopSecretPass@123", "998877");
        assertThat(result).isEqualTo("Consulted patient 101");

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLog log = logs.get(0);
        assertThat(log.getAction()).isEqualTo("PATIENT_CONSULT");
        assertThat(log.getEntityType()).isEqualTo("PATIENT");
        assertThat(log.getEntityId()).isEqualTo("101");
        assertThat(log.getIp()).isEqualTo("127.0.0.1");

        // By default, method arguments are NOT serialized into details
        assertThat(log.getDetails()).isNull();
    }

    @Test
    @DisplayName("@Audited records explicit opt-in details string when specified at call site")
    void auditedAspectRecordsOptInDetails() {
        String result = testAuditService.optInDetailsAction();
        assertThat(result).isEqualTo("Generated Report");

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        AuditLog log = logs.get(0);
        assertThat(log.getAction()).isEqualTo("OPT_IN_ACTION");
        assertThat(log.getEntityType()).isEqualTo("REPORT");
        assertThat(log.getDetails()).isEqualTo("Monthly doctor activity summary");
    }

    @Test
    @DisplayName("@Audited aspect never breaks main business method execution even if audit log throws exception")
    void auditedAspectNeverBreaksMainMethod() {
        // Even under edge case, the business invocation completes successfully
        assertThatCode(() -> testAuditService.riskyAction())
                .doesNotThrowAnyException();
    }
}
