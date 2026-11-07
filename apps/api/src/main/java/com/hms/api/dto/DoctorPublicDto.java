package com.hms.api.dto;

import java.math.BigDecimal;
import java.util.Set;

public record DoctorPublicDto(
    Long id,
    String fullName,
    String bio,
    String qualifications,
    String languages,
    int experienceYears,
    String photoUrl,
    BigDecimal consultationFee,
    Set<SpecialtyDto> specialties,
    Set<BranchDto> branches,
    boolean isActive
) {}

