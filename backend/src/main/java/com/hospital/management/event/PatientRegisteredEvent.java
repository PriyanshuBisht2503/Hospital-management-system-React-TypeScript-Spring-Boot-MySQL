package com.hospital.management.event;

public record PatientRegisteredEvent(
    Long patientId,
    String email,
    String firstName,
    String lastName
) {}
