package com.hms.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SpecialtyRequest(
    @NotBlank(message = "Specialty name is required")
    String name,

    @NotBlank(message = "Specialty code is required")
    String code,

    String description
) {}
