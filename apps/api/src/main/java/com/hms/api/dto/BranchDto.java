package com.hms.api.dto;

import com.hms.api.domain.Branch;
import jakarta.validation.constraints.NotBlank;

public record BranchDto(
    Long id,
    String name,
    String code,
    String address,
    String city,
    String phone,
    String email,
    boolean isActive
) {
    public static BranchDto from(Branch b) {
        return new BranchDto(
            b.getId(),
            b.getName(),
            b.getCode(),
            b.getAddress(),
            b.getCity(),
            b.getPhone(),
            b.getEmail(),
            b.isActive()
        );
    }
}
