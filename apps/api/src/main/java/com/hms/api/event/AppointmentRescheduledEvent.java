package com.hms.api.event;

public record AppointmentRescheduledEvent(Long oldAppointmentId, Long newAppointmentId) {}

