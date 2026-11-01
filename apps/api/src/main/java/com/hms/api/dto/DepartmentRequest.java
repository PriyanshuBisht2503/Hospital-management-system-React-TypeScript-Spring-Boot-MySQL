package com.hms.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DepartmentRequest(
    @NotBlank(message = "Department name is required")
    String name,

    @NotBlank(message = "Department code is required")
    String code,

    String description
) {}
