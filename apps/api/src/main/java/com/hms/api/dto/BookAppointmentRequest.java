package com.hms.api.dto;

import com.hms.api.domain.AppointmentType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record BookAppointmentRequest(
        @NotNull(message = "Patient ID is required")
        Long patientId,

        @NotNull(message = "Doctor ID is required")
        Long doctorId,

        @NotNull(message = "Branch ID is required")
        Long branchId,

        @NotNull(message = "Start time is required")
        Instant startTime,

        @NotNull(message = "End time is required")
        Instant endTime,

        @NotNull(message = "Appointment type is required")
        AppointmentType type,

        String reasonForVisit
) {}

