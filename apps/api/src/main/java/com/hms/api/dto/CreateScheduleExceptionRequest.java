package com.hms.api.dto;

import com.hms.api.domain.ScheduleExceptionType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateScheduleExceptionRequest(
        @NotNull(message = "Date is required")
        LocalDate date,

        LocalTime startTime,

        LocalTime endTime,

        @NotNull(message = "Exception type is required")
        ScheduleExceptionType type,

        String reason,

        boolean force
) {}

