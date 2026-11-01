package com.hms.api.web;

import com.hms.api.domain.AuditLog;
import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import com.hms.api.repository.AuditLogRepository;
import com.hms.api.repository.UserRepository;
import com.hms.api.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditLogViewerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String adminToken;
    private String doctorToken;
    private String receptionistToken;
    private String patientToken;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(new User("admin@example.com", "+971501111111", "hash", Role.ADMIN, "Admin User"));
        User doc = userRepository.save(new User("doc@example.com", "+971502222222", "hash", Role.DOCTOR, "Dr. Alice"));
        User rec = userRepository.save(new User("rec@example.com", "+971503333333", "hash", Role.RECEPTIONIST, "Receptionist User"));
        User pat = userRepository.save(new User("pat@example.com", "+971504444444", "hash", Role.PATIENT, "Patient User"));

        adminToken = "Bearer " + jwtTokenProvider.generateAccessToken(admin);
        doctorToken = "Bearer " + jwtTokenProvider.generateAccessToken(doc);
        receptionistToken = "Bearer " + jwtTokenProvider.generateAccessToken(rec);
        patientToken = "Bearer " + jwtTokenProvider.generateAccessToken(pat);
    }

    @Test
    @DisplayName("Audit log viewer: 401 unauthenticated, 403 for wrong roles")
    void auditLogViewerSecurity() throws Exception {
        // 401 unauthenticated
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isUnauthorized());

        // 403 for wrong roles
        String[] wrongTokens = { doctorToken, receptionistToken, patientToken };
        for (String token : wrongTokens) {
            mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Audit log viewer: ADMIN can query paginated logs with filters")
    void adminCanQueryAuditLogsWithFilters() throws Exception {
        auditLogRepository.save(new AuditLog(1L, "LOGIN_SUCCESS", "USER", "1", "127.0.0.1", null));
        auditLogRepository.save(new AuditLog(1L, "CREATE_BRANCH", "BRANCH", "MANK-01", "127.0.0.1", null));
        auditLogRepository.save(new AuditLog(2L, "LOGIN_FAILED", "USER", "2", "127.0.0.1", null));

        // 1. Unfiltered query
        mockMvc.perform(get("/api/admin/audit-logs")
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));

        // 2. Filter by action
        mockMvc.perform(get("/api/admin/audit-logs?action=CREATE_BRANCH")
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].action").value("CREATE_BRANCH"))
                .andExpect(jsonPath("$.content[0].entityType").value("BRANCH"));

        // 3. Filter by userId
        mockMvc.perform(get("/api/admin/audit-logs?userId=2")
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].action").value("LOGIN_FAILED"));
    }
}

