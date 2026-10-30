package com.hms.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterPatientRequest(
    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    String fullName,

    @Email(message = "Invalid email format")
    String email,

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    String phone,

    @Size(min = 8, message = "Password must be at least 8 characters")
    String password
) {
    public boolean hasContact() {
        boolean hasEmail = email != null && !email.trim().isEmpty();
        boolean hasPhone = phone != null && !phone.trim().isEmpty();
        return hasEmail || hasPhone;
    }
}
