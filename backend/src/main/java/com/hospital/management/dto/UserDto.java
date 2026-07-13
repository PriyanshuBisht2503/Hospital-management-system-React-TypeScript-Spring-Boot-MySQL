package com.hospital.management.dto;

import java.util.Set;

public record UserDto(
    Long id,
    String email,
    String firstName,
    String lastName,
    String phone,
    boolean active,
    Set<String> roles
) {}
