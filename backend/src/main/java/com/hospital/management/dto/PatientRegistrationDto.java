package com.hospital.management.dto;

import com.hospital.management.entity.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PatientRegistrationDto(
    @Valid
    @NotNull(message = "User details are required")
    UserRegistrationDto user,

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    LocalDate dateOfBirth,

    @NotNull(message = "Gender is required")
    Gender gender,

    @Size(max = 5, message = "Blood group must not exceed 5 characters")
    String bloodGroup,

    @Size(max = 255, message = "Address must not exceed 255 characters")
    String address,

    @Size(max = 100, message = "Emergency contact name must not exceed 100 characters")
    String emergencyContactName,

    @Pattern(regexp = "^\\+?[0-9\\s-]{7,20}$", message = "Invalid emergency contact phone format")
    String emergencyContactPhone
) {}
