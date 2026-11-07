package com.hms.api.dto;

import com.hms.api.domain.Department;

public record DepartmentDto(
    Long id,
    String name,
    String code,
    String description,
    boolean isActive
) {
    public static DepartmentDto from(Department d) {
        return new DepartmentDto(
            d.getId(),
            d.getName(),
            d.getCode(),
            d.getDescription(),
            d.isActive()
        );
    }
}
