package com.hms.api.dto;

import com.hms.api.domain.Specialty;

public record SpecialtyDto(
    Long id,
    String name,
    String code,
    String description,
    boolean isActive
) {
    public static SpecialtyDto from(Specialty s) {
        return new SpecialtyDto(
            s.getId(),
            s.getName(),
            s.getCode(),
            s.getDescription(),
            s.isActive()
        );
    }
}
