package com.hms.api.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
    @NotNull(message = "Enabled status is required")
    Boolean enabled
) {}
