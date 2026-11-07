package com.hms.api.service;

import com.hms.api.domain.Doctor;
import com.hms.api.domain.OtpCode;
import com.hms.api.domain.Patient;
import com.hms.api.domain.PasswordResetToken;
import com.hms.api.domain.RefreshToken;
import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import com.hms.api.dto.*;
import com.hms.api.exception.BadRequestException;
import com.hms.api.exception.InvalidTokenException;
import com.hms.api.repository.DoctorRepository;
import com.hms.api.repository.OtpCodeRepository;
import com.hms.api.repository.PatientRepository;
import com.hms.api.repository.PasswordResetTokenRepository;
import com.hms.api.repository.RefreshTokenRepository;
import com.hms.api.repository.UserRepository;
import com.hms.api.security.ClientIpResolver;
import com.hms.api.security.JwtTokenProvider;
import com.hms.api.security.RateLimiterService;
import com.hms.api.security.SecurityUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String REFRESH_COOKIE_NAME = "hms_refresh_token";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;
    private static final long REFRESH_EXPIRY_DAYS = 7;
    private static final int REFRESH_RACE_GRACE_WINDOW_SECONDS = 10;
    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int PASSWORD_RESET_EXPIRY_MINUTES = 15;

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuditLogService auditLogService;
    private final ClientIpResolver clientIpResolver;
    private final RateLimiterService rateLimiterService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.jwt.cookie.secure:false}")
    private boolean cookieSecure;

    public AuthService(
        UserRepository userRepository,
        PatientRepository patientRepository,
        RefreshTokenRepository refreshTokenRepository,
        OtpCodeRepository otpCodeRepository,
        PasswordResetTokenRepository passwordResetTokenRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider tokenProvider,
        AuditLogService auditLogService,
        ClientIpResolver clientIpResolver,
        RateLimiterService rateLimiterService,
        EmailService emailService,
        DoctorRepository doctorRepository
    ) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.otpCodeRepository = otpCodeRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.auditLogService = auditLogService;
        this.clientIpResolver = clientIpResolver;
        this.rateLimiterService = rateLimiterService;
        this.emailService = emailService;
        this.doctorRepository = doctorRepository;
    }

    /**
     * Staff & user login with email and password.
     * Prevents account enumeration by providing uniform error response times and messages.
     * Locked accounts return identical generic response as bad credentials; lockout logged in audit only.
     * Rate limited by client IP and attempted email target.
     */
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpReq, HttpServletResponse httpRes) {
        String clientIp = clientIpResolver.resolveClientIp(httpReq);
        String email = request.email() != null ? request.email().trim().toLowerCase() : "";

        // Bucket4j Rate Limiting: Keyed by Client IP and Attempted Email
        rateLimiterService.checkLoginIpLimit(clientIp);
        rateLimiterService.checkLoginEmailLimit(email);

        // Enforce minimum password length
        if (request.password() == null || request.password().length() < 8) {
            auditLogService.log(null, "LOGIN_FAILED", "USER", email, clientIp, "Password length under 8 characters");
            throw new BadCredentialsException("Invalid email or password");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            // Perform dummy password check to avoid timing attacks / enumeration
            passwordEncoder.matches("dummy_password_timing_check", "$2a$10$dummyHashToEqualizeVerificationTime000000000000000000000");
            auditLogService.log(null, "LOGIN_FAILED", "USER", email, clientIp, "Non-existent account login attempt");
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userOpt.get();

        // Check account lock: Return identical generic message as bad password to prevent enumeration
        if (user.isAccountLocked()) {
            auditLogService.log(user.getId(), "LOGIN_BLOCKED", "USER", String.valueOf(user.getId()), clientIp, "Account is locked until " + user.getLockedUntil());
            throw new BadCredentialsException("Invalid email or password");
        }

        // Check disabled: Return identical generic message
        if (!user.isEnabled()) {
            auditLogService.log(user.getId(), "LOGIN_FAILED", "USER", String.valueOf(user.getId()), clientIp, "Disabled account attempted login");
            throw new BadCredentialsException("Invalid email or password");
        }

        // Verify password
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int newFailedCount = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(newFailedCount);

            if (newFailedCount >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
                auditLogService.log(user.getId(), "ACCOUNT_LOCKED", "USER", String.valueOf(user.getId()), clientIp, "Exceeded 5 failed attempts; locked for 15 minutes");
            } else {
                auditLogService.log(user.getId(), "LOGIN_FAILED", "USER", String.valueOf(user.getId()), clientIp, "Failed attempt " + newFailedCount + " of 5");
            }

            userRepository.save(user);
            throw new BadCredentialsException("Invalid email or password");
        }

        // Reset failed login count and lockout upon successful credentials
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // Generate tokens
        String accessToken = tokenProvider.generateAccessToken(user);
        String rawRefreshToken = tokenProvider.generateRawRefreshToken();
        String hashedRefreshToken = tokenProvider.hashToken(rawRefreshToken);

        RefreshToken refreshToken = new RefreshToken(
            hashedRefreshToken,
            user,
            LocalDateTime.now().plusDays(REFRESH_EXPIRY_DAYS)
        );
        refreshTokenRepository.save(refreshToken);

        // Set HttpOnly Cookie
        setRefreshCookie(httpRes, rawRefreshToken, (int) (REFRESH_EXPIRY_DAYS * 24 * 3600));

        auditLogService.log(user.getId(), "LOGIN_SUCCESS", "USER", String.valueOf(user.getId()), clientIp, "Successful login for role " + user.getRole());

        return AuthResponse.of(
            accessToken,
            tokenProvider.getAccessTokenExpirationMs() / 1000,
            UserSummaryDto.fromEntity(user, resolveDoctorId(user))
        );
    }

    /**
     * Sends OTP code for patient authentication.
     * Uniform response to prevent account enumeration.
     * Rate limited by client IP.
     */
    @Transactional
    public MessageResponse sendOtp(OtpSendRequest request, HttpServletRequest httpReq) {
        String clientIp = clientIpResolver.resolveClientIp(httpReq);

        String target = request.target() != null ? request.target().trim().toLowerCase() : "";
        if (target.isEmpty()) {
            throw new BadRequestException("Target (email or phone) is required");
        }

        // Rate limit: by client IP (5/min) and per-target (3 per 10 mins)
        rateLimiterService.checkOtpLimit(clientIp);
        rateLimiterService.checkOtpTargetLimit(target);

        // Delete any existing OTPs for this target
        otpCodeRepository.deleteByTarget(target);

        // Generate 6-digit numeric OTP
        int codeInt = secureRandom.nextInt(1_000_000);
        String rawCode = String.format("%06d", codeInt);
        String codeHash = tokenProvider.hashToken(rawCode);

        OtpCode otpCode = new OtpCode(
            target,
            codeHash,
            LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES)
        );
        otpCodeRepository.save(otpCode);

        // Dispatch OTP (never logged outside dev profile)
        emailService.sendOtp(target, rawCode);

        auditLogService.log(null, "OTP_SENT", "OTP", target, clientIp, "One-time code generated and sent");

        return MessageResponse.of("If the account exists or is eligible, a one-time verification code has been sent.");
    }

    /**
     * Verifies patient OTP code and authenticates or provisions the PATIENT account.
     * Rate limited by client IP.
     */
    @Transactional
    public AuthResponse verifyOtp(OtpVerifyRequest request, HttpServletRequest httpReq, HttpServletResponse httpRes) {
        String clientIp = clientIpResolver.resolveClientIp(httpReq);
        rateLimiterService.checkOtpLimit(clientIp);

        String target = request.target() != null ? request.target().trim().toLowerCase() : "";
        String code = request.code() != null ? request.code().trim() : "";

        Optional<OtpCode> otpOpt = otpCodeRepository.findTopByTargetOrderByCreatedAtDesc(target);

        if (otpOpt.isEmpty() || otpOpt.get().isExpired()) {
            auditLogService.log(null, "OTP_FAILED", "OTP", target, clientIp, "Expired or missing OTP code");
            throw new BadRequestException("Invalid or expired verification code");
        }

        OtpCode otp = otpOpt.get();

        if (otp.getAttempts() >= MAX_OTP_ATTEMPTS) {
            otpCodeRepository.delete(otp);
            auditLogService.log(null, "OTP_BLOCKED", "OTP", target, clientIp, "Exceeded max OTP attempts");
            throw new BadRequestException("Maximum verification attempts exceeded. Please request a new code.");
        }

        otp.setAttempts(otp.getAttempts() + 1);
        otpCodeRepository.save(otp);

        String inputHash = tokenProvider.hashToken(code);
        if (!inputHash.equals(otp.getCodeHash())) {
            auditLogService.log(null, "OTP_FAILED", "OTP", target, clientIp, "Incorrect OTP code");
            throw new BadRequestException("Invalid or expired verification code");
        }

        // Code matched; consume OTP
        otpCodeRepository.delete(otp);

        // Find or create PATIENT user
        boolean isEmail = target.contains("@");
        Optional<User> userOpt = isEmail ? userRepository.findByEmail(target) : userRepository.findByPhone(target);

        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            if (!user.isEnabled() || user.isAccountLocked()) {
                auditLogService.log(user.getId(), "LOGIN_BLOCKED", "USER", String.valueOf(user.getId()), clientIp, "Disabled or locked patient account attempted OTP login");
                throw new BadRequestException("Account is inactive or locked. Please contact support.");
            }
        } else {
            // Auto-provision patient account on first verified OTP
            String email = isEmail ? target : null;
            String phone = !isEmail ? target : null;
            user = new User(email, phone, null, Role.PATIENT, "Patient");
            userRepository.save(user);
            auditLogService.log(user.getId(), "USER_REGISTERED_OTP", "USER", String.valueOf(user.getId()), clientIp, "Auto-provisioned patient via verified OTP");
        }

        // Rule 10: Link a Patient to a User ONLY after OTP verification of that exact email or phone.
        if (user.getRole() == Role.PATIENT && patientRepository.findByUserId(user.getId()).isEmpty()) {
            Optional<Patient> existingPatient = isEmail 
                    ? patientRepository.findByEmail(target) 
                    : patientRepository.findByPhone(target);
            if (existingPatient.isPresent() && existingPatient.get().getUser() == null) {
                Patient patient = existingPatient.get();
                patient.setUser(user);
                patientRepository.save(patient);
                auditLogService.log(user.getId(), "PATIENT_LINKED_OTP", "PATIENT", String.valueOf(patient.getId()), clientIp, "Linked existing patient record to verified user via OTP");
            } else {
                Patient newPatient = new Patient(user.getFullName(), user.getPhone(), user.getEmail(), null, null);
                newPatient.setUser(user);
                patientRepository.save(newPatient);
                auditLogService.log(user.getId(), "PATIENT_CREATED_OTP", "PATIENT", String.valueOf(newPatient.getId()), clientIp, "Created new patient record for verified user via OTP");
            }
        }

        // Generate tokens
        String accessToken = tokenProvider.generateAccessToken(user);
        String rawRefreshToken = tokenProvider.generateRawRefreshToken();
        String hashedRefreshToken = tokenProvider.hashToken(rawRefreshToken);

        RefreshToken refreshToken = new RefreshToken(
            hashedRefreshToken,
            user,
            LocalDateTime.now().plusDays(REFRESH_EXPIRY_DAYS)
        );
        refreshTokenRepository.save(refreshToken);

        setRefreshCookie(httpRes, rawRefreshToken, (int) (REFRESH_EXPIRY_DAYS * 24 * 3600));

        auditLogService.log(user.getId(), "LOGIN_SUCCESS", "USER", String.valueOf(user.getId()), clientIp, "Successful OTP login for PATIENT");

        return AuthResponse.of(
            accessToken,
            tokenProvider.getAccessTokenExpirationMs() / 1000,
            UserSummaryDto.fromEntity(user)
        );
    }

    /**
     * Self-registration for PATIENT role only.
     * Enforces minimum password length and ensures at least one of email or phone.
     */
    /**
     * Patient self-registration.
     * Creates account with strictly PATIENT role and sends verification OTP.
     * Does NOT issue tokens directly; user verifies OTP to authenticate.
     */
    @Transactional
    public MessageResponse registerPatient(RegisterPatientRequest request, HttpServletRequest httpReq) {
        String clientIp = clientIpResolver.resolveClientIp(httpReq);

        if (!request.hasContact()) {
            throw new BadRequestException("At least one of email or phone number is required");
        }

        String email = request.email() != null && !request.email().isBlank() ? request.email().trim().toLowerCase() : null;
        String phone = request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null;

        if (email != null && userRepository.findByEmail(email).isPresent()) {
            throw new BadRequestException("An account with this email already exists");
        }

        if (phone != null && userRepository.findByPhone(phone).isPresent()) {
            throw new BadRequestException("An account with this phone number already exists");
        }

        String passwordHash = null;
        if (request.password() != null && !request.password().isBlank()) {
            if (request.password().length() < 8) {
                throw new BadRequestException("Password must be at least 8 characters");
            }
            passwordHash = passwordEncoder.encode(request.password());
        }

        // Fixed Role: strictly PATIENT
        User user = new User(email, phone, passwordHash, Role.PATIENT, request.fullName().trim());
        userRepository.save(user);

        // Send verification OTP to patient contact (email or phone)
        String target = email != null ? email : phone;
        rateLimiterService.checkOtpLimit(clientIp);
        rateLimiterService.checkOtpTargetLimit(target);

        otpCodeRepository.deleteByTarget(target);
        int codeInt = secureRandom.nextInt(1_000_000);
        String rawCode = String.format("%06d", codeInt);
        String codeHash = tokenProvider.hashToken(rawCode);

        OtpCode otpCode = new OtpCode(
            target,
            codeHash,
            LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES)
        );
        otpCodeRepository.save(otpCode);
        emailService.sendOtp(target, rawCode);

        auditLogService.log(user.getId(), "PATIENT_REGISTERED", "USER", String.valueOf(user.getId()), clientIp, "Patient self-registered successfully");
        auditLogService.log(user.getId(), "OTP_SENT", "OTP", target, clientIp, "One-time registration verification code sent");

        return MessageResponse.of("Account registered successfully. A verification code has been sent to complete your registration.");
    }

    /**
     * Request password reset token.
     * Uniform response to prevent account enumeration.
     * Rate limited by client IP.
     */
    @Transactional
    public MessageResponse requestPasswordReset(PasswordResetRequest request, HttpServletRequest httpReq) {
        String clientIp = clientIpResolver.resolveClientIp(httpReq);
        rateLimiterService.checkPasswordResetLimit(clientIp);

        String email = request.email() != null ? request.email().trim().toLowerCase() : "";
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent() && userOpt.get().isEnabled()) {
            User user = userOpt.get();

            // Clear previous reset tokens for this user
            passwordResetTokenRepository.deleteByUser(user);

            // Generate secure reset token
            String rawToken = tokenProvider.generateRawRefreshToken();
            String tokenHash = tokenProvider.hashToken(rawToken);

            PasswordResetToken resetToken = new PasswordResetToken(
                tokenHash,
                user,
                LocalDateTime.now().plusMinutes(PASSWORD_RESET_EXPIRY_MINUTES)
            );
            passwordResetTokenRepository.save(resetToken);

            emailService.sendPasswordReset(user.getEmail(), rawToken);
            auditLogService.log(user.getId(), "PASSWORD_RESET_REQUESTED", "USER", String.valueOf(user.getId()), clientIp, "Password reset token generated");
        } else {
            auditLogService.log(null, "PASSWORD_RESET_REQUESTED", "USER", email, clientIp, "Password reset attempted for non-existent or inactive email");
        }

        return MessageResponse.of("If the account exists, password reset instructions have been sent.");
    }

    /**
     * Confirms password reset, updates password hash, clears lockout, and revokes all user sessions.
     * Rate limited by client IP.
     */
    @Transactional
    public MessageResponse confirmPasswordReset(PasswordResetConfirmRequest request, HttpServletRequest httpReq) {
        String clientIp = clientIpResolver.resolveClientIp(httpReq);
        rateLimiterService.checkPasswordResetLimit(clientIp);

        if (request.newPassword() == null || request.newPassword().length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters");
        }

        String tokenHash = tokenProvider.hashToken(request.token().trim());
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid()) {
            auditLogService.log(null, "PASSWORD_RESET_FAILED", "TOKEN", "unknown", clientIp, "Invalid or expired reset token");
            throw new BadRequestException("Invalid or expired password reset token");
        }

        PasswordResetToken resetToken = tokenOpt.get();
        User user = resetToken.getUser();

        // Mark token used
        resetToken.setUsed(true);
        passwordResetTokenRepository.saveAndFlush(resetToken);

        // Update password and reset failure counters
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.saveAndFlush(user);

        // Revoke all existing sessions for security
        refreshTokenRepository.revokeAllByUser(user);

        auditLogService.log(user.getId(), "PASSWORD_RESET_COMPLETED", "USER", String.valueOf(user.getId()), clientIp, "Password reset completed successfully");

        return MessageResponse.of("Password has been successfully reset. Please log in with your new password.");
    }

    /**
     * Rotates refresh token:
     * - Verifies existing refresh token
     * - Reusing a revoked token within the 10-second grace window is treated as a race condition, not an attack
     * - Reusing a revoked token outside the 10-second grace window triggers revocation of ALL refresh tokens
     * - Rechecks that user is enabled in the DB and issues new access token with the current DB role
     */
    @Transactional
    public AuthResponse refreshToken(String rawRefreshToken, HttpServletRequest httpReq, HttpServletResponse httpRes) {
        String clientIp = clientIpResolver.resolveClientIp(httpReq);

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidTokenException("Missing refresh token");
        }

        String tokenHash = tokenProvider.hashToken(rawRefreshToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            clearRefreshCookie(httpRes);
            auditLogService.log(null, "TOKEN_REFRESH_FAILED", "TOKEN", "unknown", clientIp, "Refresh token hash not found");
            throw new InvalidTokenException("Invalid refresh token");
        }

        RefreshToken token = tokenOpt.get();

        // Reload fresh user from DB to guarantee current state and role
        User user = userRepository.findById(token.getUser().getId())
            .orElseThrow(() -> new InvalidTokenException("User record not found"));

        // Recheck enabled status on refresh
        if (!user.isEnabled() || user.isAccountLocked()) {
            clearRefreshCookie(httpRes);
            auditLogService.log(user.getId(), "TOKEN_REFRESH_FAILED", "USER", String.valueOf(user.getId()), clientIp, "Disabled or locked user attempted token refresh");
            throw new InvalidTokenException("Account is disabled or locked. Please contact administrator.");
        }

        // Handle revoked token: check 10-second grace window for race condition
        if (token.isRevoked()) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime revokedAt = token.getRevokedAt();
            boolean withinGraceWindow = revokedAt != null && revokedAt.isAfter(now.minusSeconds(REFRESH_RACE_GRACE_WINDOW_SECONDS));

            if (withinGraceWindow) {
                auditLogService.log(user.getId(), "TOKEN_REFRESH_RACE", "USER", String.valueOf(user.getId()), clientIp, "Refresh token rotated within 10s grace window");
                clearRefreshCookie(httpRes);
                throw new InvalidTokenException("Refresh token was recently rotated. Please retry with latest credentials.");
            }

            auditLogService.log(user.getId(), "TOKEN_REUSE_ATTACK", "USER", String.valueOf(user.getId()), clientIp, "Revoked refresh token reuse detected! Revoking all sessions.");
            refreshTokenRepository.revokeAllByUser(user);
            clearRefreshCookie(httpRes);
            throw new InvalidTokenException("Revoked refresh token reuse detected. All sessions have been terminated.");
        }

        if (token.isExpired()) {
            clearRefreshCookie(httpRes);
            auditLogService.log(user.getId(), "TOKEN_REFRESH_EXPIRED", "USER", String.valueOf(user.getId()), clientIp, "Refresh token expired");
            throw new InvalidTokenException("Refresh token has expired. Please log in again.");
        }

        // Revoke the current token with timestamp (Rotation)
        token.setRevoked(true);
        token.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);

        // Issue new tokens with current role from database
        String newAccessToken = tokenProvider.generateAccessToken(user);
        String newRawRefreshToken = tokenProvider.generateRawRefreshToken();
        String newHashedRefreshToken = tokenProvider.hashToken(newRawRefreshToken);

        RefreshToken newRefreshToken = new RefreshToken(
            newHashedRefreshToken,
            user,
            LocalDateTime.now().plusDays(REFRESH_EXPIRY_DAYS)
        );
        refreshTokenRepository.save(newRefreshToken);

        setRefreshCookie(httpRes, newRawRefreshToken, (int) (REFRESH_EXPIRY_DAYS * 24 * 3600));

        auditLogService.log(user.getId(), "TOKEN_REFRESH", "USER", String.valueOf(user.getId()), clientIp, "Rotated refresh token successfully");

        return AuthResponse.of(
            newAccessToken,
            tokenProvider.getAccessTokenExpirationMs() / 1000,
            UserSummaryDto.fromEntity(user, resolveDoctorId(user))
        );
    }

    /**
     * Logout revokes the refresh token and clears the HttpOnly cookie.
     */
    @Transactional
    public void logout(String rawRefreshToken, HttpServletRequest httpReq, HttpServletResponse httpRes) {
        String clientIp = clientIpResolver.resolveClientIp(httpReq);
        Long userId = null;

        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = tokenProvider.hashToken(rawRefreshToken);
            Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
            if (tokenOpt.isPresent()) {
                RefreshToken token = tokenOpt.get();
                token.setRevoked(true);
                token.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(token);
                userId = token.getUser().getId();
            }
        }

        clearRefreshCookie(httpRes);
        auditLogService.log(userId, "LOGOUT", "USER", userId != null ? String.valueOf(userId) : "anonymous", clientIp, "User logged out");
    }

    @Transactional(readOnly = true)
    public UserSummaryDto getCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUser securityUser)) {
            throw new InvalidTokenException("User is not authenticated");
        }

        User user = userRepository.findById(securityUser.getId())
                .orElseThrow(() -> new InvalidTokenException("User record not found"));

        if (!user.isEnabled() || user.isAccountLocked()) {
            throw new InvalidTokenException("User account is inactive or locked");
        }

        return UserSummaryDto.fromEntity(user, resolveDoctorId(user));
    }

    private Long resolveDoctorId(User user) {
        if (user.getRole() == Role.DOCTOR) {
            return doctorRepository.findByUserId(user.getId()).map(Doctor::getId).orElse(null);
        }
        return null;
    }

    public static String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private void setRefreshCookie(HttpServletResponse response, String tokenValue, int maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, tokenValue)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(maxAgeSeconds)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
