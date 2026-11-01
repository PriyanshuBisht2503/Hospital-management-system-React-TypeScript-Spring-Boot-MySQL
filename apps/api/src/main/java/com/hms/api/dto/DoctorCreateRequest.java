package com.hms.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;

public record DoctorCreateRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,

    @NotBlank(message = "Phone number is required")
    String phone,

    @NotBlank(message = "Doctor full name is required")
    String fullName,

    @NotBlank(message = "Initial password is required")
    @Size(min = 8, message = "Initial password must be at least 8 characters")
    String password,

    String bio,
    String qualifications,
    String languages,

    @Min(value = 0, message = "Experience years cannot be negative")
    int experienceYears,

    String photoUrl,

    @NotNull(message = "Consultation fee is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Consultation fee must be non-negative")
    BigDecimal consultationFee,

    Set<Long> specialtyIds,
    Set<Long> branchIds
) {}

