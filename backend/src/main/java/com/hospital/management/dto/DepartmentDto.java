package com.hospital.management.dto;

public record DepartmentDto(
    Long id,
    String name,
    String code,
    String description
) {}
