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
    private com.hms.api.security.RateLimiterService rateLimiterService;

    @Autowired
    private ObjectMapper objectMapper;

    private User testDoctor;

    @BeforeEach
    void setUp() {
        rateLimiterService.clear();
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();

        testDoctor = new User(
            "doctor.test@example.com",
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
        LoginRequest req = new LoginRequest("doctor.test@example.com", "Doctor@12345");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.email").value("doctor.test@example.com"))
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
        LoginRequest req = new LoginRequest("doctor.test@example.com", "WrongPassword123");

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
        LoginRequest req = new LoginRequest("nonexistent@example.com", "SomePassword123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "LOGIN_FAILED".equals(l.getAction()) && "nonexistent@example.com".equals(l.getEntityId()));
    }

    @Test
    @DisplayName("Account lockout after 5 failures returns generic 401 response; audit log records lockout")
    void accountLockoutAfterFiveFailures() throws Exception {
        LoginRequest badReq = new LoginRequest("doctor.test@example.com", "WrongPassword123");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(badReq)))
                    .andExpect(status().isUnauthorized());
        }

        User lockedUser = userRepository.findById(testDoctor.getId()).orElseThrow();
        assertThat(lockedUser.getFailedLoginCount()).isEqualTo(5);
        assertThat(lockedUser.isAccountLocked()).isTrue();

        // 6th attempt returns identical generic 401 response (no enumeration)
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "ACCOUNT_LOCKED".equals(l.getAction()));
        assertThat(logs).anyMatch(l -> "LOGIN_BLOCKED".equals(l.getAction()));
    }

    @Test
    @DisplayName("Lockout expires after 15 minutes allowing successful login and resetting counters")
    void lockoutExpiresAfterFifteenMinutes() throws Exception {
        testDoctor.setFailedLoginCount(5);
        testDoctor.setLockedUntil(LocalDateTime.now().minusMinutes(1)); // Lockout elapsed
        userRepository.save(testDoctor);

        LoginRequest validReq = new LoginRequest("doctor.test@example.com", "Doctor@12345");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        User reloaded = userRepository.findById(testDoctor.getId()).orElseThrow();
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(0);
        assertThat(reloaded.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("Disabled user cannot log in and receives identical generic 401 response")
    void disabledUserCannotLogIn() throws Exception {
        testDoctor.setEnabled(false);
        userRepository.save(testDoctor);

        LoginRequest req = new LoginRequest("doctor.test@example.com", "Doctor@12345");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "LOGIN_FAILED".equals(l.getAction()));
    }

    @Test
    @DisplayName("Existing valid access token stops working immediately when user is disabled in database")
    void existingTokenStopsWorkingWhenUserIsDisabled() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(testDoctor);

        // Verify token works initially
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Disable user in DB
        testDoctor.setEnabled(false);
        userRepository.save(testDoctor);

        // Same token is now rejected on next request
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint without token returns 401 Unauthorized")
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Expired access token returns 401 Unauthorized")
    void expiredAccessTokenReturns401() throws Exception {
        String expiredToken = jwtTokenProvider.generateAccessToken(testDoctor, -1000);

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Tampered access token returns 401 Unauthorized")
    void tamperedAccessTokenReturns401() throws Exception {
        String validToken = jwtTokenProvider.generateAccessToken(testDoctor);
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "xyz99";

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refresh token rotation: issues new access token & new cookie, revoking old token")
    void refreshTokenRotation() throws Exception {
        LoginRequest req = new LoginRequest("doctor.test@example.com", "Doctor@12345");
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

        // Verify old token is now revoked in database with timestamp
        String oldHash = jwtTokenProvider.hashToken(refreshCookie1.getValue());
        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(oldHash).orElseThrow();
        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(oldToken.getRevokedAt()).isNotNull();

        // Verify new token is valid in database
        String newHash = jwtTokenProvider.hashToken(refreshCookie2.getValue());
        RefreshToken newToken = refreshTokenRepository.findByTokenHash(newHash).orElseThrow();
        assertThat(newToken.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("Refresh race: reuse within 10-second grace window is treated as race, NOT reuse attack")
    void refreshRaceWithinGraceWindowDoesNotRevokeAllSessions() throws Exception {
        LoginRequest req = new LoginRequest("doctor.test@example.com", "Doctor@12345");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Cookie cookie1 = loginResult.getResponse().getCookie("hms_refresh_token");

        // Rotate once (cookie1 is revoked, revokedAt is set to now)
        mockMvc.perform(post("/api/auth/refresh")
                .cookie(cookie1))
                .andExpect(status().isOk());

        // Reusing cookie1 within grace window (just rotated a split second ago)
        mockMvc.perform(post("/api/auth/refresh")
                .cookie(cookie1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("recently rotated")));

        // Verify active sessions for this user were NOT wiped out
        List<RefreshToken> allTokens = refreshTokenRepository.findAll();
        assertThat(allTokens).anyMatch(t -> !t.isRevoked());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "TOKEN_REFRESH_RACE".equals(l.getAction()));
        assertThat(logs).noneMatch(l -> "TOKEN_REUSE_ATTACK".equals(l.getAction()));
    }

    @Test
    @DisplayName("Reusing a revoked refresh token outside 10s grace window revokes ALL refresh tokens for user")
    void revokedRefreshTokenReuseOutsideGraceWindowRevokesAllUserSessions() throws Exception {
        // 1. Login twice
        LoginRequest req = new LoginRequest("doctor.test@example.com", "Doctor@12345");
        MvcResult login1 = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Cookie cookie1 = login1.getResponse().getCookie("hms_refresh_token");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        // 2. Rotate cookie1 once
        mockMvc.perform(post("/api/auth/refresh")
                .cookie(cookie1))
                .andExpect(status().isOk());

        // 3. Simulate elapsed grace window by adjusting revokedAt to 15 seconds ago
        String hash1 = jwtTokenProvider.hashToken(cookie1.getValue());
        RefreshToken rotatedToken = refreshTokenRepository.findByTokenHash(hash1).orElseThrow();
        rotatedToken.setRevokedAt(LocalDateTime.now().minusSeconds(15));
        refreshTokenRepository.save(rotatedToken);

        // 4. Malicious actor reuses old cookie1 outside grace window
        mockMvc.perform(post("/api/auth/refresh")
                .cookie(cookie1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Revoked refresh token reuse detected")));

        // 5. Verify all refresh tokens for this user are now revoked in DB
        List<RefreshToken> allTokens = refreshTokenRepository.findAll();
        assertThat(allTokens).isNotEmpty();
        assertThat(allTokens).allMatch(RefreshToken::isRevoked);

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "TOKEN_REUSE_ATTACK".equals(l.getAction()));
    }

    @Test
    @DisplayName("Client IP resolution ignores spoofed X-Forwarded-For when trust-proxy-headers is false")
    void clientIpIgnoresSpoofedXForwardedFor() throws Exception {
        LoginRequest req = new LoginRequest("doctor.test@example.com", "WrongPassword123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "203.0.113.195")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "LOGIN_FAILED".equals(l.getAction()) && "127.0.0.1".equals(l.getIp()));
    }

    @Test
    @DisplayName("Long email truncates safely in audit log without overflowing 50-char column")
    void longEmailTruncatesInAuditLog() throws Exception {
        String longEmail = "averylongnonexistentemailaddressfortestingoverlowexceeding50chars@example.com";
        LoginRequest req = new LoginRequest(longEmail, "WrongPassword123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "LOGIN_FAILED".equals(l.getAction()) &&
                l.getEntityId().length() <= 50 &&
                longEmail.startsWith(l.getEntityId()));
    }

    @Test
    @DisplayName("Logout revokes the refresh token and clears cookie")
    void logoutRevokesToken() throws Exception {
        LoginRequest req = new LoginRequest("doctor.test@example.com", "Doctor@12345");
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
    @DisplayName("GET /api/auth/me returns current user profile when authenticated")
    void getMeEndpoint() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken(testDoctor);

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testDoctor.getId()))
                .andExpect(jsonPath("$.email").value("doctor.test@example.com"))
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.fullName").value("Dr. Test Doctor"));
    }
}
