package com.hms.api.dto;

import com.hms.api.domain.ScheduleException;
import com.hms.api.domain.ScheduleExceptionType;
import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleExceptionDto(
        Long id,
        Long doctorId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        ScheduleExceptionType type,
        String reason
) {
    public static ScheduleExceptionDto fromEntity(ScheduleException e) {
        if (e == null) return null;
        return new ScheduleExceptionDto(
                e.getId(),
                e.getDoctor() != null ? e.getDoctor().getId() : null,
                e.getDate(),
                e.getStartTime(),
                e.getEndTime(),
                e.getType(),
                e.getReason()
        );
    }
}

