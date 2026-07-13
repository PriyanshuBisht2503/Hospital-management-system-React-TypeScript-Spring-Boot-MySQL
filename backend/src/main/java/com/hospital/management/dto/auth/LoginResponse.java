package com.hospital.management.dto.auth;

import java.util.Set;

public record LoginResponse(
    String accessToken,
    String email,
    String firstName,
    String lastName,
    Set<String> roles,
    Set<String> permissions
) {}
