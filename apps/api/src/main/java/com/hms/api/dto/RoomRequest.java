package com.hms.api.dto;

import com.hms.api.domain.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoomRequest(
    @NotBlank(message = "Room number is required")
    String roomNumber,

    @NotNull(message = "Branch ID is required")
    Long branchId,

    @NotNull(message = "Department ID is required")
    Long departmentId,

    @NotNull(message = "Room type is required")
    RoomType type
) {}
