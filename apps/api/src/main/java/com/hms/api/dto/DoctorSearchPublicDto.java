package com.hms.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record DoctorSearchPublicDto(
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
        boolean isActive,
        Instant nextAvailableSlot
) {}

