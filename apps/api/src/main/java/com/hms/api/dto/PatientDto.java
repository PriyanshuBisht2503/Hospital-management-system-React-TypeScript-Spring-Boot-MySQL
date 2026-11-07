package com.hms.api.dto;

import com.hms.api.domain.Gender;
import com.hms.api.domain.Patient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

public record PatientDto(
        Long id,
        Long userId,
        String fullName,
        String phone,
        String email,
        LocalDate dateOfBirth,
        Integer age,
        Gender gender,
        Instant createdAt
) {
    public static PatientDto fromEntity(Patient patient, boolean maskContact, Clock clock) {
        if (patient == null) return null;

        Integer age = null;
        if (patient.getDateOfBirth() != null) {
            age = Period.between(patient.getDateOfBirth(), LocalDate.now(clock)).getYears();
        }

        return new PatientDto(
                patient.getId(),
                patient.getUser() != null ? patient.getUser().getId() : null,
                patient.getFullName(),
                maskContact ? null : patient.getPhone(),
                maskContact ? null : patient.getEmail(),
                patient.getDateOfBirth(),
                age,
                patient.getGender(),
                patient.getCreatedAt()
        );
    }
}

