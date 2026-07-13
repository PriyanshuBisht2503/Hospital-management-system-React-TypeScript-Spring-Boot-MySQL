package com.hospital.management.event;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentBookedEvent(
    Long appointmentId,
    Long patientId,
    Long doctorId,
    LocalDate appointmentDate,
    LocalTime timeSlot,
    int tokenNumber
) {}
