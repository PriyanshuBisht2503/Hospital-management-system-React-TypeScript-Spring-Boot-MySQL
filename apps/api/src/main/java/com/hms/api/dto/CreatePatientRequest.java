package com.hms.api.dto;

import com.hms.api.domain.Gender;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CreatePatientRequest(
        @NotBlank(message = "Full name is required")
        String fullName,
        String phone,
        String email,
        LocalDate dateOfBirth,
        Gender gender
) {
    public boolean hasContact() {
        return (phone != null && !phone.trim().isEmpty()) || (email != null && !email.trim().isEmpty());
    }
}

