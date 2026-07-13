package com.hospital.management.dto.auth;

public record TokenRefreshResponse(
    String accessToken,
    String refreshToken
) {}
