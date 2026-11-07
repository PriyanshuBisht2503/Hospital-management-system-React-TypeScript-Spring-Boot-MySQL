package com.hms.api.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpSendRequest(
    @NotBlank(message = "Target (email or phone) is required")
    String target
) {}
