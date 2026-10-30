package com.hms.api.service;

import com.hms.api.domain.RefreshToken;
import com.hms.api.domain.User;
import com.hms.api.dto.AuthResponse;
import com.hms.api.dto.LoginRequest;
import com.hms.api.dto.UserSummaryDto;
import com.hms.api.exception.InvalidTokenException;
import com.hms.api.repository.RefreshTokenRepository;
import com.hms.api.repository.UserRepository;
import com.hms.api.security.ClientIpResolver;
import com.hms.api.security.JwtTokenProvider;
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

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuditLogService auditLogService;
    private final ClientIpResolver clientIpResolver;

    @Value("${app.jwt.cookie.secure:false}")
    private boolean cookieSecure;

    public AuthService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider tokenProvider,
        AuditLogService auditLogService,
        ClientIpResolver clientIpResolver
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.auditLogService = auditLogService;
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * Staff & user login with email and password.
     * Prevents account enumeration by providing uniform error response times and messages.
     * Locked accounts return identical generic response as bad credentials; lockout logged in audit only.
     */
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpReq, HttpServletResponse httpRes) {
        String clientIp = clientIpResolver.resolveClientIp(httpReq);
        String email = request.email() != null ? request.email().trim().toLowerCase() : "";

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
            UserSummaryDto.fromEntity(user)
        );
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
            UserSummaryDto.fromEntity(user)
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

        return UserSummaryDto.fromEntity(user);
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
