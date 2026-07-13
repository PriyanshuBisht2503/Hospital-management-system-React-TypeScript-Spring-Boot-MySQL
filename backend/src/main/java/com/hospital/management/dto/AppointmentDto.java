package com.hospital.management.dto;

import com.hospital.management.entity.AppointmentStatus;
import com.hospital.management.entity.QueueStatus;
import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentDto(
    Long id,
    PatientDto patient,
    DoctorDto doctor,
    LocalDate appointmentDate,
    LocalTime timeSlot,
    AppointmentStatus status,
    int tokenNumber,
    QueueStatus queueStatus
) {}
