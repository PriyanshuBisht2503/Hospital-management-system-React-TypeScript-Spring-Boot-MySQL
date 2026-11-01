package com.hms.api.dto;

import jakarta.validation.constraints.NotBlank;

public record BranchRequest(
    @NotBlank(message = "Branch name is required")
    String name,

    @NotBlank(message = "Branch code is required")
    String code,

    @NotBlank(message = "Branch address is required")
    String address,

    @NotBlank(message = "Branch city is required")
    String city,

    String phone,
    String email
) {}
