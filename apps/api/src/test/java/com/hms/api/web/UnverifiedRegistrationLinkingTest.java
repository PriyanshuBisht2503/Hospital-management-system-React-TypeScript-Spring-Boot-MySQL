package com.hms.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.Gender;
import com.hms.api.domain.Patient;
import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import com.hms.api.dto.OtpVerifyRequest;
import com.hms.api.dto.RegisterPatientRequest;
import com.hms.api.repository.*;
import com.hms.api.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UnverifiedRegistrationLinkingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpCodeRepository otpCodeRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        patientRepository.deleteAll();
        otpCodeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        patientRepository.deleteAll();
        otpCodeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Rule 10: Unverified registration does NOT claim an existing walk-in record; only OTP verification links it")
    void unverifiedRegistrationDoesNotClaimWalkInRecord() throws Exception {
        String testEmail = "walkin.patient@example.com";
        String testPhone = "+919876500000";

        // 1. Staff creates a walk-in patient record (no user linked)
        Patient walkIn = new Patient("Walkin Patient", testPhone, testEmail, LocalDate.of(1990, 1, 1), Gender.MALE);
        walkIn = patientRepository.save(walkIn);
        assertThat(walkIn.getUser()).isNull();

        // 2. Someone registers with the same email/phone
        RegisterPatientRequest regReq = new RegisterPatientRequest("Walkin Patient", testEmail, testPhone, "password123");
        mockMvc.perform(post("/api/auth/register-patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated());

        // Verify: User is created, but the walk-in patient record is STILL NOT LINKED
        Patient unverifiedCheck = patientRepository.findById(walkIn.getId()).orElseThrow();
        assertThat(unverifiedCheck.getUser())
                .as("Unverified registration must NEVER claim an existing patient record")
                .isNull();

        // 3. Now verify OTP with the exact target
        // Save a known OTP code in repository
        String rawOtp = "123456";
        com.hms.api.domain.OtpCode otpCode = new com.hms.api.domain.OtpCode(
                testEmail,
                tokenProvider.hashToken(rawOtp),
                LocalDateTime.now().plusMinutes(5)
        );
        otpCodeRepository.save(otpCode);

        OtpVerifyRequest verifyReq = new OtpVerifyRequest(testEmail, rawOtp);
        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk());

        // Verify: After OTP verification of exact email, the patient record is now linked to the verified user!
        Patient verifiedCheck = patientRepository.findById(walkIn.getId()).orElseThrow();
        assertThat(verifiedCheck.getUser())
                .as("Verified OTP must link the patient record to the verified user")
                .isNotNull();
        assertThat(verifiedCheck.getUser().getEmail()).isEqualTo(testEmail);
    }
}
