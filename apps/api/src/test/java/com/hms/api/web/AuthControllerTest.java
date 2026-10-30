package com.hms.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.AuditLog;
import com.hms.api.domain.RefreshToken;
import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import com.hms.api.dto.LoginRequest;
import com.hms.api.repository.AuditLogRepository;
import com.hms.api.repository.RefreshTokenRepository;
import com.hms.api.repository.UserRepository;
import com.hms.api.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User testDoctor;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();

        testDoctor = new User(
            "doctor.test@aster.ae",
            "+971501112233",
            passwordEncoder.encode("Doctor@12345"),
            Role.DOCTOR,
            "Dr. Test Doctor"
        );
        userRepository.save(testDoctor);
    }

    @Test
    @DisplayName("Staff login success: returns access token, sets HttpOnly refresh cookie, and logs audit")
    void staffLoginSuccess() throws Exception {
        LoginRequest req = new LoginRequest("doctor.test@aster.ae", "Doctor@12345");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.email").value("doctor.test@aster.ae"))
                .andExpect(jsonPath("$.user.role").value("DOCTOR"))
                .andExpect(cookie().exists("hms_refresh_token"))
                .andExpect(cookie().httpOnly("hms_refresh_token", true))
                .andReturn();

        // Verify refresh token is stored hashed in DB
        Cookie cookie = result.getResponse().getCookie("hms_refresh_token");
        assertThat(cookie).isNotNull();
        String rawToken = cookie.getValue();
        String tokenHash = jwtTokenProvider.hashToken(rawToken);

        assertThat(refreshTokenRepository.findByTokenHash(tokenHash)).isPresent();

        // Verify audit log
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "LOGIN_SUCCESS".equals(l.getAction()) &&
                String.valueOf(testDoctor.getId()).equals(l.getEntityId()));
    }

    @Test
    @DisplayName("Staff login failure with bad password: returns 401 and records audit")
    void staffLoginBadPassword() throws Exception {
        LoginRequest req = new LoginRequest("doctor.test@aster.ae", "WrongPassword123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        User reloaded = userRepository.findById(testDoctor.getId()).orElseThrow();
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(1);

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "LOGIN_FAILED".equals(l.getAction()));
    }

    @Test
    @DisplayName("Login response is identical for non-existent account (no account enumeration)")
    void staffLoginNonExistentAccount() throws Exception {
        LoginRequest req = new LoginRequest("nonexistent@aster.ae", "SomePassword123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "LOGIN_FAILED".equals(l.getAction()) && "nonexistent@aster.ae".equals(l.getEntityId()));
    }

    @Test
    @DisplayName("Account lockout after 5 consecutive failed logins (15 min lock)")
    void accountLockoutAfterFiveFailures() throws Exception {
        LoginRequest badReq = new LoginRequest("doctor.test@aster.ae", "WrongPassword123");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(badReq)))
                    .andExpect(status().isUnauthorized());
        }

        User lockedUser = userRepository.findById(testDoctor.getId()).orElseThrow();
        assertThat(lockedUser.getFailedLoginCount()).isEqualTo(5);
        assertThat(lockedUser.isAccountLocked()).isTrue();

        // 6th attempt should be blocked with 403 Forbidden
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("temporarily locked")));

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "ACCOUNT_LOCKED".equals(l.getAction()));
    }

    @Test
    @DisplayName("Refresh token rotation: issues new access token & new cookie, revoking old token")
    void refreshTokenRotation() throws Exception {
        // Initial login
        LoginRequest req = new LoginRequest("doctor.test@aster.ae", "Doctor@12345");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie1 = loginResult.getResponse().getCookie("hms_refresh_token");
        assertThat(refreshCookie1).isNotNull();

        // Refresh request
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                .cookie(refreshCookie1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(cookie().exists("hms_refresh_token"))
                .andReturn();

        Cookie refreshCookie2 = refreshResult.getResponse().getCookie("hms_refresh_token");
        assertThat(refreshCookie2).isNotNull();
        assertThat(refreshCookie2.getValue()).isNotEqualTo(refreshCookie1.getValue());

        // Verify old token is now revoked in database
        String oldHash = jwtTokenProvider.hashToken(refreshCookie1.getValue());
        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(oldHash).orElseThrow();
        assertThat(oldToken.isRevoked()).isTrue();

        // Verify new token is valid in database
        String newHash = jwtTokenProvider.hashToken(refreshCookie2.getValue());
        RefreshToken newToken = refreshTokenRepository.findByTokenHash(newHash).orElseThrow();
        assertThat(newToken.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("Reusing a revoked refresh token triggers revocation of ALL refresh tokens for that user")
    void revokedRefreshTokenReuseRevokesAllUserSessions() throws Exception {
        // 1. Login twice to create 2 active refresh tokens
        LoginRequest req = new LoginRequest("doctor.test@aster.ae", "Doctor@12345");
        MvcResult login1 = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Cookie cookie1 = login1.getResponse().getCookie("hms_refresh_token");

        MvcResult login2 = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Cookie cookie2 = login2.getResponse().getCookie("hms_refresh_token");

        // 2. Rotate cookie1 once (this revokes cookie1)
        mockMvc.perform(post("/api/auth/refresh")
                .cookie(cookie1))
                .andExpect(status().isOk());

        // 3. Malicious actor reuses old cookie1 (revoked token reuse attack!)
        mockMvc.perform(post("/api/auth/refresh")
                .cookie(cookie1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Revoked refresh token reuse detected")));

        // 4. Verify all refresh tokens for this user are now revoked in DB
        List<RefreshToken> allTokens = refreshTokenRepository.findAll();
        assertThat(allTokens).isNotEmpty();
        assertThat(allTokens).allMatch(RefreshToken::isRevoked);

        // 5. Verify security audit log recorded the attack
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "TOKEN_REUSE_ATTACK".equals(l.getAction()));
    }

    @Test
    @DisplayName("Logout revokes the refresh token and clears cookie")
    void logoutRevokesToken() throws Exception {
        LoginRequest req = new LoginRequest("doctor.test@aster.ae", "Doctor@12345");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        Cookie cookie = loginResult.getResponse().getCookie("hms_refresh_token");

        mockMvc.perform(post("/api/auth/logout")
                .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("hms_refresh_token", 0));

        String tokenHash = jwtTokenProvider.hashToken(cookie.getValue());
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(token.isRevoked()).isTrue();

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "LOGOUT".equals(l.getAction()));
    }

    @Test
    @DisplayName("GET /api/auth/me returns current user profile when authenticated, 403 when unauthenticated")
    void getMeEndpoint() throws Exception {
        // Without token -> 403 Forbidden
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());

        // With valid access token
        String accessToken = jwtTokenProvider.generateAccessToken(testDoctor);

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testDoctor.getId()))
                .andExpect(jsonPath("$.email").value("doctor.test@aster.ae"))
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.fullName").value("Dr. Test Doctor"));
    }
}
