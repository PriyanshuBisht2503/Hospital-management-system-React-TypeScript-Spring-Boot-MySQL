package com.hms.api.dto;

import com.hms.api.domain.Appointment;
import com.hms.api.domain.AppointmentStatus;
import com.hms.api.domain.AppointmentType;

import java.time.Clock;
import java.time.Instant;

public record AppointmentResponseDto(
        Long id,
        PatientSummaryDto patient,
        Long doctorId,
        String doctorName,
        Long branchId,
        String branchName,
        Instant startTime,
        Instant endTime,
        AppointmentType type,
        AppointmentStatus status,
        String reasonForVisit,
        String cancelReason,
        String cancelledBy,
        String createdBy,
        Long rescheduledFromId,
        boolean needsRescheduling,
        Instant confirmedAt,
        Instant checkedInAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        Instant createdAt
) {
    public static AppointmentResponseDto fromEntity(Appointment a, boolean maskPatientContact, Clock clock) {
        if (a == null) return null;

        String docName = a.getDoctor() != null && a.getDoctor().getUser() != null
                ? a.getDoctor().getUser().getFullName() : null;
        String branchName = a.getBranch() != null ? a.getBranch().getName() : null;

        return new AppointmentResponseDto(
                a.getId(),
                PatientSummaryDto.fromEntity(a.getPatient(), maskPatientContact, clock),
                a.getDoctor() != null ? a.getDoctor().getId() : null,
                docName,
                a.getBranch() != null ? a.getBranch().getId() : null,
                branchName,
                a.getStartTime(),
                a.getEndTime(),
                a.getType(),
                a.getStatus(),
                a.getReasonForVisit(),
                a.getCancelReason(),
                a.getCancelledBy(),
                a.getCreatedBy(),
                a.getRescheduledFrom() != null ? a.getRescheduledFrom().getId() : null,
                a.isNeedsRescheduling(),
                a.getConfirmedAt(),
                a.getCheckedInAt(),
                a.getStartedAt(),
                a.getCompletedAt(),
                a.getCancelledAt(),
                a.getCreatedAt()
        );
    }
}

