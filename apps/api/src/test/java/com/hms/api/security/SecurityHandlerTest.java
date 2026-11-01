package com.hms.api.security;

import com.hms.api.domain.AuditLog;
import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import com.hms.api.repository.AuditLogRepository;
import com.hms.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityHandlerTest.TestEndpointsConfig.class)
class SecurityHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @TestConfiguration
    static class TestEndpointsConfig {
        @RestController
        static class TestSecuredController {
            @GetMapping("/api/test-admin-endpoint")
            @PreAuthorize("hasRole('ADMIN')")
            public String adminOnly() {
                return "Admin Access Granted";
            }
        }
    }

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("401 Unauthorized with NO token returns consistent ErrorResponse and does NOT write to audit log")
    void unauthenticatedRequestWithoutTokenReturns401WithoutAudit() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Full authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        // Zero audit logs for requests with no token
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).isEmpty();
    }

    @Test
    @DisplayName("401 Unauthorized WITH invalid Authorization header returns consistent ErrorResponse and audits UNAUTHORIZED_ACCESS")
    void unauthenticatedRequestWithInvalidTokenReturns401AndAudits() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer invalid.expired.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Full authentication is required to access this resource"));

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo("UNAUTHORIZED_ACCESS");
        assertThat(logs.get(0).getEntityId()).isEqualTo("/api/auth/me");
    }

    @Test
    @DisplayName("403 Forbidden returns consistent ErrorResponse JSON and creates exactly ONE audit row")
    void forbiddenRequestReturnsConsistent403AndExactlyOneAuditRow() throws Exception {
        User doctorUser = new User("doc@example.com", "+971509998811", "pass", Role.DOCTOR, "Dr. Test");
        doctorUser = userRepository.save(doctorUser);
        String doctorToken = jwtTokenProvider.generateAccessToken(doctorUser);

        // Doctor attempting to access an ADMIN-only endpoint
        mockMvc.perform(get("/api/test-admin-endpoint")
                .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/test-admin-endpoint"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        List<AuditLog> logs = auditLogRepository.findAll();
        // Verifies exactly ONE audit row is created
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo("ACCESS_DENIED");
        assertThat(logs.get(0).getEntityId()).isEqualTo("/api/test-admin-endpoint");
        assertThat(logs.get(0).getUserId()).isEqualTo(doctorUser.getId());
    }
}
