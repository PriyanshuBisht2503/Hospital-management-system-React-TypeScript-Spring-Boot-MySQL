package com.hms.api.web;

import com.hms.api.dto.*;
import com.hms.api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for login, token refresh, logout, patient OTP, registration, and password reset")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Staff and user login", description = "Authenticates with email and password, returns 30-min JWT access token and sets 7-day HttpOnly refresh cookie")
    public ResponseEntity<AuthResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        AuthResponse response = authService.login(request, httpRequest, httpResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token", description = "Reads refresh token from HttpOnly cookie, rotates it, and returns new access token")
    public ResponseEntity<AuthResponse> refresh(
        @CookieValue(name = "hms_refresh_token", required = false) String cookieRefreshToken,
        @RequestBody(required = false) Map<String, String> body,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        String token = cookieRefreshToken;
        if (token == null && body != null) {
            token = body.get("refreshToken");
        }
        AuthResponse response = authService.refreshToken(token, httpRequest, httpResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out", description = "Revokes refresh token in database and clears the refresh cookie")
    public ResponseEntity<MessageResponse> logout(
        @CookieValue(name = "hms_refresh_token", required = false) String cookieRefreshToken,
        @RequestBody(required = false) Map<String, String> body,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        String token = cookieRefreshToken;
        if (token == null && body != null) {
            token = body.get("refreshToken");
        }
        authService.logout(token, httpRequest, httpResponse);
        return ResponseEntity.ok(MessageResponse.of("Logged out successfully"));
    }

    @PostMapping("/otp/send")
    @Operation(summary = "Send OTP code", description = "Generates and sends a 6-digit verification code to patient email or phone")
    public ResponseEntity<MessageResponse> sendOtp(
        @Valid @RequestBody OtpSendRequest request,
        HttpServletRequest httpRequest
    ) {
        MessageResponse response = authService.sendOtp(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP code", description = "Verifies OTP and returns access token + refresh cookie; auto-provisions patient if new")
    public ResponseEntity<AuthResponse> verifyOtp(
        @Valid @RequestBody OtpVerifyRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        AuthResponse response = authService.verifyOtp(request, httpRequest, httpResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register-patient")
    @Operation(summary = "Register patient", description = "Self-registration endpoint for PATIENT role only; creates account and sends OTP for verification")
    public ResponseEntity<MessageResponse> registerPatient(
        @Valid @RequestBody RegisterPatientRequest request,
        HttpServletRequest httpRequest
    ) {
        MessageResponse response = authService.registerPatient(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/password-reset/request")
    @Operation(summary = "Request password reset", description = "Dispatches password reset token via email with uniform response")
    public ResponseEntity<MessageResponse> requestPasswordReset(
        @Valid @RequestBody PasswordResetRequest request,
        HttpServletRequest httpRequest
    ) {
        MessageResponse response = authService.requestPasswordReset(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Confirm password reset", description = "Verifies token, updates password, and revokes all active user sessions")
    public ResponseEntity<MessageResponse> confirmPasswordReset(
        @Valid @RequestBody PasswordResetConfirmRequest request,
        HttpServletRequest httpRequest
    ) {
        MessageResponse response = authService.confirmPasswordReset(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<UserSummaryDto> getMe(Authentication authentication) {
        UserSummaryDto user = authService.getCurrentUser(authentication);
        return ResponseEntity.ok(user);
    }
}
