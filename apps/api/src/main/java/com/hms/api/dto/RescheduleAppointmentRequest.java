package com.hms.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleAppointmentRequest(
        @NotNull(message = "New start time is required")
        Instant newStartTime,

        @NotNull(message = "New end time is required")
        Instant newEndTime,

        Long newBranchId,

        String reason
) {}

