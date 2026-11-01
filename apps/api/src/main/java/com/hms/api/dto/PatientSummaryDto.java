package com.hms.api.dto;

import com.hms.api.domain.Patient;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

public record PatientSummaryDto(
        Long id,
        String fullName,
        Integer age,
        String phone,
        String email
) {
    public static PatientSummaryDto fromEntity(Patient patient, boolean maskContact, Clock clock) {
        if (patient == null) {
            return null;
        }

        Integer age = null;
        if (patient.getDateOfBirth() != null) {
            LocalDate today = LocalDate.now(clock);
            age = Period.between(patient.getDateOfBirth(), today).getYears();
        }

        return new PatientSummaryDto(
                patient.getId(),
                patient.getFullName(),
                age,
                maskContact ? null : patient.getPhone(),
                maskContact ? null : patient.getEmail()
        );
    }
}

