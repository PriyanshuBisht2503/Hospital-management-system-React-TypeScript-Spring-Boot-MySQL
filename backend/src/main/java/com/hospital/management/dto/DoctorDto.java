package com.hospital.management.dto;

import java.math.BigDecimal;

public record DoctorDto(
    Long id,
    UserDto user,
    DepartmentDto department,
    String specialization,
    String licenseNumber,
    BigDecimal consultationFee
) {}
