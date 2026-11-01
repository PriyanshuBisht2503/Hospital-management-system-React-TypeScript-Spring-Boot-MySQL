package com.hms.api.dto;

import com.hms.api.domain.DoctorSchedule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record DoctorScheduleDto(
        Long id,
        Long doctorId,
        Long branchId,
        String branchName,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        int slotMinutes,
        LocalTime breakStart,
        LocalTime breakEnd,
        LocalDate validFrom,
        LocalDate validTo,
        boolean isActive
) {
    public static DoctorScheduleDto fromEntity(DoctorSchedule s) {
        if (s == null) return null;
        return new DoctorScheduleDto(
                s.getId(),
                s.getDoctor() != null ? s.getDoctor().getId() : null,
                s.getBranch() != null ? s.getBranch().getId() : null,
                s.getBranch() != null ? s.getBranch().getName() : null,
                s.getDayOfWeek(),
                s.getStartTime(),
                s.getEndTime(),
                s.getSlotMinutes(),
                s.getBreakStart(),
                s.getBreakEnd(),
                s.getValidFrom(),
                s.getValidTo(),
                s.isActive()
        );
    }
}

