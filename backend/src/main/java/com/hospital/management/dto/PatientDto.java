package com.hospital.management.dto;

import com.hospital.management.entity.Gender;
import java.time.LocalDate;

public record PatientDto(
    Long id,
    UserDto user,
    LocalDate dateOfBirth,
    Gender gender,
    String bloodGroup,
    String address,
    String emergencyContactName,
    String emergencyContactPhone
) {}
