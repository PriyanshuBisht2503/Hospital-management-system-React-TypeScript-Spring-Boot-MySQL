package com.hms.api.dto;

import java.time.Instant;

public record AvailableSlotDto(
        Long doctorId,
        Long branchId,
        Instant startTime,
        Instant endTime,
        int slotMinutes
) {}

