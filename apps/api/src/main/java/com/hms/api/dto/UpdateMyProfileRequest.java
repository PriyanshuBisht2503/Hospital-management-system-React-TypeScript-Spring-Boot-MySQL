package com.hms.api.dto;

import com.hms.api.domain.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Fields a PATIENT may edit on their own profile. Email and all ids are immutable here.
 */
public record UpdateMyProfileRequest(
    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    String fullName,

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    String phone,

    LocalDate dateOfBirth,

    Gender gender
) {}
