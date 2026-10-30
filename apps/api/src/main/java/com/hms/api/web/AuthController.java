package com.hms.api.web;

import com.hms.api.dto.AuthResponse;
import com.hms.api.dto.LoginRequest;
import com.hms.api.dto.UserSummaryDto;
import com.hms.api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for login, token refresh, logout, and identity inspection")
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
    public ResponseEntity<Map<String, String>> logout(
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
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<UserSummaryDto> getMe(Authentication authentication) {
        UserSummaryDto user = authService.getCurrentUser(authentication);
        return ResponseEntity.ok(user);
    }
}
