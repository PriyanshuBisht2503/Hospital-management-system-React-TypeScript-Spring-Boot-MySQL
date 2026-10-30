package com.hms.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.*;
import com.hms.api.dto.*;
import com.hms.api.repository.AuditLogRepository;
import com.hms.api.repository.OtpCodeRepository;
import com.hms.api.repository.PasswordResetTokenRepository;
import com.hms.api.repository.RefreshTokenRepository;
import com.hms.api.repository.UserRepository;
import com.hms.api.security.JwtTokenProvider;
import com.hms.api.security.RateLimiterService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Step3BAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OtpCodeRepository otpCodeRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

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
        rateLimiterService.clear();
        refreshTokenRepository.deleteAll();
        otpCodeRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ==========================================
    // PATIENT OTP TESTS
    // ==========================================

    @Test
    @DisplayName("Patient OTP send: uniform response for existing and non-existing accounts (no enumeration)")
    void patientOtpSendUniformResponse() throws Exception {
        OtpSendRequest req = new OtpSendRequest("patient1@aster.ae");

        mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the account exists or is eligible, a one-time verification code has been sent."));

        // Verify OTP is hashed in DB
        Optional<OtpCode> saved = otpCodeRepository.findTopByTargetOrderByCreatedAtDesc("patient1@aster.ae");
        assertThat(saved).isPresent();
        assertThat(saved.get().getCodeHash()).isNotBlank();
        assertThat(saved.get().isExpired()).isFalse();

        // Second call with different target returns identical response
        OtpSendRequest nonExistent = new OtpSendRequest("random999@aster.ae");
        mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nonExistent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the account exists or is eligible, a one-time verification code has been sent."));
    }

    @Test
    @DisplayName("Patient OTP verify success: provisions PATIENT user and issues tokens with HttpOnly cookie")
    void patientOtpVerifySuccessAndAutoProvision() throws Exception {
        String target = "newpatient@aster.ae";
        String rawCode = "123456";
        String hashedCode = jwtTokenProvider.hashToken(rawCode);

        otpCodeRepository.save(new OtpCode(target, hashedCode, LocalDateTime.now().plusMinutes(5)));

        OtpVerifyRequest req = new OtpVerifyRequest(target, rawCode);

        MvcResult result = mockMvc.perform(post("/api/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(target))
                .andExpect(jsonPath("$.user.role").value("PATIENT"))
                .andExpect(cookie().exists("hms_refresh_token"))
                .andExpect(cookie().httpOnly("hms_refresh_token", true))
                .andReturn();

        // Verify patient user was created in database
        User createdUser = userRepository.findByEmail(target).orElseThrow();
        assertThat(createdUser.getRole()).isEqualTo(Role.PATIENT);

        // Verify OTP was consumed
        assertThat(otpCodeRepository.findTopByTargetOrderByCreatedAtDesc(target)).isEmpty();
    }

    @Test
    @DisplayName("Patient OTP verify failure: invalid code increments attempts and fails with 400")
    void patientOtpVerifyInvalidCode() throws Exception {
        String target = "patient@aster.ae";
        String rawCode = "123456";
        String hashedCode = jwtTokenProvider.hashToken(rawCode);

        otpCodeRepository.save(new OtpCode(target, hashedCode, LocalDateTime.now().plusMinutes(5)));

        OtpVerifyRequest wrongReq = new OtpVerifyRequest(target, "999999");

        mockMvc.perform(post("/api/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrongReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired verification code"));

        OtpCode reloaded = otpCodeRepository.findTopByTargetOrderByCreatedAtDesc(target).orElseThrow();
        assertThat(reloaded.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Patient OTP verify: exceeding 5 attempts deletes OTP and blocks verification")
    void patientOtpVerifyExceedingAttempts() throws Exception {
        String target = "patient@aster.ae";
        String rawCode = "123456";
        String hashedCode = jwtTokenProvider.hashToken(rawCode);

        OtpCode code = new OtpCode(target, hashedCode, LocalDateTime.now().plusMinutes(5));
        code.setAttempts(5);
        otpCodeRepository.save(code);

        OtpVerifyRequest req = new OtpVerifyRequest(target, rawCode);

        mockMvc.perform(post("/api/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Maximum verification attempts exceeded")));

        // OTP is deleted
        assertThat(otpCodeRepository.findTopByTargetOrderByCreatedAtDesc(target)).isEmpty();
    }

    // ==========================================
    // PATIENT SELF-REGISTRATION TESTS
    // ==========================================

    @Test
    @DisplayName("Patient self-registration: valid data registers PATIENT and returns JWT with refresh cookie")
    void registerPatientSuccess() throws Exception {
        RegisterPatientRequest req = new RegisterPatientRequest(
            "Fatima Al Mansoori",
            "fatima@aster.ae",
            "+971501234567",
            "Password@123"
        );

        mockMvc.perform(post("/api/auth/register-patient")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("fatima@aster.ae"))
                .andExpect(jsonPath("$.user.role").value("PATIENT"))
                .andExpect(cookie().exists("hms_refresh_token"));

        User saved = userRepository.findByEmail("fatima@aster.ae").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.PATIENT);
        assertThat(passwordEncoder.matches("Password@123", saved.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Patient registration fails if neither email nor phone is provided")
    void registerPatientFailsWithoutContact() throws Exception {
        RegisterPatientRequest req = new RegisterPatientRequest(
            "Anonymous Patient",
            null,
            null,
            "Password@123"
        );

        mockMvc.perform(post("/api/auth/register-patient")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("At least one of email or phone number is required")));
    }

    @Test
    @DisplayName("Patient registration fails if password is less than 8 characters")
    void registerPatientFailsShortPassword() throws Exception {
        RegisterPatientRequest req = new RegisterPatientRequest(
            "Short Pass Patient",
            "short@aster.ae",
            "+971509998877",
            "short"
        );

        mockMvc.perform(post("/api/auth/register-patient")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ==========================================
    // PASSWORD RESET TESTS
    // ==========================================

    @Test
    @DisplayName("Password reset request: uniform response for existing and non-existing email (no enumeration)")
    void passwordResetRequestUniformResponse() throws Exception {
        User user = new User("doctor@aster.ae", "+971501112244", passwordEncoder.encode("OldPassword123"), Role.DOCTOR, "Dr. Reset");
        userRepository.save(user);

        // Existing user
        PasswordResetRequest req = new PasswordResetRequest("doctor@aster.ae");
        mockMvc.perform(post("/api/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the account exists, password reset instructions have been sent."));

        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
        assertThat(tokens).isNotEmpty();

        // Non-existent user
        PasswordResetRequest fakeReq = new PasswordResetRequest("ghost@aster.ae");
        mockMvc.perform(post("/api/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(fakeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the account exists, password reset instructions have been sent."));
    }

    @Test
    @DisplayName("Password reset confirm: updates password hash, clears lockout counters, and revokes all refresh tokens")
    void passwordResetConfirmSuccess() throws Exception {
        User user = new User("locked@aster.ae", "+971501119999", passwordEncoder.encode("OldPassword123"), Role.PATIENT, "Locked Patient");
        user.setFailedLoginCount(5);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        // Add an active refresh token
        RefreshToken activeToken = new RefreshToken("dummy_hash_1", user, LocalDateTime.now().plusDays(7));
        refreshTokenRepository.save(activeToken);

        String rawToken = "my-secure-password-reset-token-123456789";
        String tokenHash = jwtTokenProvider.hashToken(rawToken);
        passwordResetTokenRepository.save(new PasswordResetToken(tokenHash, user, LocalDateTime.now().plusMinutes(15)));

        PasswordResetConfirmRequest confirmReq = new PasswordResetConfirmRequest(rawToken, "NewSecurePassword@123");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Password has been successfully reset")));

        User reloadedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewSecurePassword@123", reloadedUser.getPasswordHash())).isTrue();
        assertThat(reloadedUser.getFailedLoginCount()).isEqualTo(0);
        assertThat(reloadedUser.getLockedUntil()).isNull();

        // All active refresh tokens revoked
        List<RefreshToken> allTokens = refreshTokenRepository.findAll();
        assertThat(allTokens).allMatch(RefreshToken::isRevoked);

        // Token marked used
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(resetToken.isUsed()).isTrue();
    }

    @Test
    @DisplayName("Password reset confirm fails if token is invalid or already used")
    void passwordResetConfirmFailsForUsedToken() throws Exception {
        User user = new User("user@aster.ae", "+971508887766", passwordEncoder.encode("OldPass@123"), Role.PATIENT, "Patient User");
        userRepository.save(user);

        String rawToken = "already-used-token";
        String tokenHash = jwtTokenProvider.hashToken(rawToken);
        PasswordResetToken usedToken = new PasswordResetToken(tokenHash, user, LocalDateTime.now().plusMinutes(15));
        usedToken.setUsed(true);
        passwordResetTokenRepository.save(usedToken);

        PasswordResetConfirmRequest req = new PasswordResetConfirmRequest(rawToken, "BrandNewPassword@123");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired password reset token"));
    }

    // ==========================================
    // BUCKET4J RATE LIMITING TESTS
    // ==========================================

    @Test
    @DisplayName("Rate limiting on OTP endpoint: returns 429 Too Many Requests after 5 attempts from same IP")
    void otpRateLimitingEnforced() throws Exception {
        OtpSendRequest req = new OtpSendRequest("ratelimit@aster.ae");

        // 5 requests allowed
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/otp/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        // 6th request from same IP is rate-limited
        mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Too many OTP requests")));
    }

    @Test
    @DisplayName("Rate limiting on login endpoint by attempted email: returns 429 after 10 attempts against same email")
    void loginRateLimitingByEmailEnforced() throws Exception {
        User user = new User("target@aster.ae", "+971501113355", passwordEncoder.encode("Secret@123"), Role.PATIENT, "Target User");
        userRepository.save(user);

        LoginRequest badReq = new LoginRequest("target@aster.ae", "WrongPassword123");

        // 10 requests against the target email
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(badReq)))
                    .andExpect(status().isUnauthorized());
        }

        // 11th request against same target email returns 429 Too Many Requests
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Too many login attempts for this account")));
    }
}
