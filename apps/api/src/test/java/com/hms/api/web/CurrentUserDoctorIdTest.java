package com.hms.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.Doctor;
import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import com.hms.api.dto.LoginRequest;
import com.hms.api.repository.AuditLogRepository;
import com.hms.api.repository.DoctorRepository;
import com.hms.api.repository.RefreshTokenRepository;
import com.hms.api.repository.UserRepository;
import com.hms.api.security.JwtTokenProvider;
import com.hms.api.security.RateLimiterService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CurrentUserDoctorIdTest {

    private static final String PASSWORD = "Password@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        cleanAll();
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        rateLimiterService.clear();
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        doctorRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createUser(String email, Role role) {
        User user = new User(email, "+971500000000", passwordEncoder.encode(PASSWORD), role, email);
        return userRepository.save(user);
    }

    private Doctor createDoctorProfile(User user) {
        Doctor doctor = new Doctor(user, "Bio", "MBBS", "English", 5, BigDecimal.valueOf(300));
        return doctorRepository.save(doctor);
    }

    private MvcResult login(String email) throws Exception {
        LoginRequest request = new LoginRequest(email, PASSWORD);
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void assertUserDoctorIdNull(String json) throws Exception {
        JsonNode doctorId = objectMapper.readTree(json).get("user").get("doctorId");
        assertThat(doctorId == null || doctorId.isNull())
                .as("Expected doctorId to be null or absent, got: %s", json)
                .isTrue();
    }

    @Test
    @DisplayName("DOCTOR login returns their own doctorId")
    void doctorLoginReturnsDoctorId() throws Exception {
        User user = createUser("doc.login@example.com", Role.DOCTOR);
        Doctor doctor = createDoctorProfile(user);

        MvcResult result = login("doc.login@example.com");

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("user").get("doctorId").asLong()).isEqualTo(doctor.getId());
    }

    @Test
    @DisplayName("DOCTOR refresh returns their own doctorId")
    void doctorRefreshReturnsDoctorId() throws Exception {
        User user = createUser("doc.refresh@example.com", Role.DOCTOR);
        Doctor doctor = createDoctorProfile(user);

        MvcResult loginResult = login("doc.refresh@example.com");
        Cookie refreshCookie = loginResult.getResponse().getCookie("hms_refresh_token");
        assertThat(refreshCookie).isNotNull();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("user").get("doctorId").asLong()).isEqualTo(doctor.getId());
    }

    @Test
    @DisplayName("GET /api/auth/me returns doctorId for DOCTOR")
    void doctorMeReturnsDoctorId() throws Exception {
        User user = createUser("doc.me@example.com", Role.DOCTOR);
        Doctor doctor = createDoctorProfile(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user);

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(doctor.getId()));
    }

    @Test
    @DisplayName("ADMIN current-user response has null doctorId")
    void adminLoginReturnsNullDoctorId() throws Exception {
        createUser("admin.null@example.com", Role.ADMIN);

        MvcResult result = login("admin.null@example.com");

        assertUserDoctorIdNull(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("RECEPTIONIST current-user response has null doctorId")
    void receptionistLoginReturnsNullDoctorId() throws Exception {
        createUser("rec.null@example.com", Role.RECEPTIONIST);

        MvcResult result = login("rec.null@example.com");

        assertUserDoctorIdNull(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("PATIENT current-user response has null doctorId")
    void patientLoginReturnsNullDoctorId() throws Exception {
        createUser("pat.null@example.com", Role.PATIENT);

        MvcResult result = login("pat.null@example.com");

        assertUserDoctorIdNull(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("DOCTOR without a doctor profile gets null doctorId and can still log in")
    void doctorWithoutProfileGetsNullDoctorIdAndCanLogin() throws Exception {
        createUser("doc.noprofile@example.com", Role.DOCTOR);

        MvcResult result = login("doc.noprofile@example.com");

        assertUserDoctorIdNull(result.getResponse().getContentAsString());
    }
}
