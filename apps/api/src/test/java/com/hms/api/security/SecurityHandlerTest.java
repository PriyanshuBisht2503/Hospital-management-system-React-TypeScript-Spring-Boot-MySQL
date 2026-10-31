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
    @DisplayName("401 Unauthorized returns consistent ErrorResponse JSON and records UNAUTHORIZED_ACCESS to audit log")
    void unauthenticatedRequestReturnsConsistent401AndAudits() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Full authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l ->
            "UNAUTHORIZED_ACCESS".equals(l.getAction()) &&
            "/api/auth/me".equals(l.getEntityId())
        );
    }

    @Test
    @DisplayName("403 Forbidden returns consistent ErrorResponse JSON and records ACCESS_DENIED to audit log")
    void forbiddenRequestReturnsConsistent403AndAudits() throws Exception {
        User doctorUser = new User("doc@aster.ae", "+971509998811", "pass", Role.DOCTOR, "Dr. Test");
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
        assertThat(logs).anyMatch(l ->
            "ACCESS_DENIED".equals(l.getAction()) &&
            "/api/test-admin-endpoint".equals(l.getEntityId())
        );
    }
}
