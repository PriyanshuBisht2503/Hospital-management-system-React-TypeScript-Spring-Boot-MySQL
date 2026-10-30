package com.hms.api.dto;

import com.hms.api.domain.Role;
import com.hms.api.domain.User;

public record UserSummaryDto(
    Long id,
    String email,
    String phone,
    Role role,
    String fullName,
    boolean enabled
) {
    public static UserSummaryDto fromEntity(User user) {
        return new UserSummaryDto(
            user.getId(),
            user.getEmail(),
            user.getPhone(),
            user.getRole(),
            user.getFullName(),
            user.isEnabled()
        );
    }
}
