package com.hms.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateDoctorScheduleRequest(
        @NotNull(message = "Branch ID is required")
        Long branchId,

        @NotNull(message = "Day of week is required")
        DayOfWeek dayOfWeek,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        LocalTime endTime,

        @NotNull(message = "Slot minutes is required")
        @Min(value = 5, message = "Slot duration must be at least 5 minutes")
        @Max(value = 120, message = "Slot duration must not exceed 120 minutes")
        Integer slotMinutes,

        LocalTime breakStart,
        LocalTime breakEnd,

        @NotNull(message = "Valid from date is required")
        LocalDate validFrom,

        LocalDate validTo
) {}

