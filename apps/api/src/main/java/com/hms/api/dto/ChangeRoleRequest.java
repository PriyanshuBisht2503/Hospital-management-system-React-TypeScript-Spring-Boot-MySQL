package com.hms.api.dto;

import com.hms.api.domain.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
    @NotNull(message = "Role is required")
    Role role
) {}

