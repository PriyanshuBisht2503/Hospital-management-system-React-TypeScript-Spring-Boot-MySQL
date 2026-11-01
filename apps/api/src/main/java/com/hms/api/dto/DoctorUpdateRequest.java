package com.hms.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.Set;

public record DoctorUpdateRequest(
    String fullName,
    String phone,
    String bio,
    String qualifications,
    String languages,

    @Min(value = 0, message = "Experience years cannot be negative")
    Integer experienceYears,

    String photoUrl,

    @DecimalMin(value = "0.0", inclusive = true, message = "Consultation fee must be non-negative")
    BigDecimal consultationFee,

    Set<Long> specialtyIds,
    Set<Long> branchIds
) {}

