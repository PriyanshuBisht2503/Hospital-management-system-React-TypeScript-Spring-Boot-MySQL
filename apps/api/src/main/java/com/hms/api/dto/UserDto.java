package com.hms.api.dto;

import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import java.time.LocalDateTime;

public record UserDto(
    Long id,
    String email,
    String phone,
    Role role,
    String fullName,
    boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static UserDto from(User u) {
        return new UserDto(
            u.getId(),
            u.getEmail(),
            u.getPhone(),
            u.getRole(),
            u.getFullName(),
            u.isEnabled(),
            u.getCreatedAt(),
            u.getUpdatedAt()
        );
    }
}

