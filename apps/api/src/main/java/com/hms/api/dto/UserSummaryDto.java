package com.hms.api.dto;

import com.hms.api.domain.Role;
import com.hms.api.domain.User;

public record UserSummaryDto(
    Long id,
    String email,
    String phone,
    Role role,
    String fullName,
    boolean enabled,
    Long doctorId
) {
    public static UserSummaryDto fromEntity(User user) {
        return fromEntity(user, null);
    }

    public static UserSummaryDto fromEntity(User user, Long doctorId) {
        return new UserSummaryDto(
            user.getId(),
            user.getEmail(),
            user.getPhone(),
            user.getRole(),
            user.getFullName(),
            user.isEnabled(),
            doctorId
        );
    }
}
