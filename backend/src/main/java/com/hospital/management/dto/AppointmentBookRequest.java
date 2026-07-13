package com.hospital.management.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentBookRequest(
    @NotNull(message = "Patient ID is required")
    Long patientId,

    @NotNull(message = "Doctor ID is required")
    Long doctorId,

    @NotNull(message = "Appointment date is required")
    @Future(message = "Appointment date must be in the future")
    LocalDate appointmentDate,

    @NotNull(message = "Time slot is required")
    LocalTime timeSlot
) {}
