package com.hms.api.dto;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    UserSummaryDto user
) {
    public static AuthResponse of(String accessToken, long expiresIn, UserSummaryDto user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}
