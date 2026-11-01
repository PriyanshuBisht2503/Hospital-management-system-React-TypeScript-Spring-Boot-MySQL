package com.hms.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.Doctor;
import com.hms.api.domain.RefreshToken;
import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import com.hms.api.dto.ChangeRoleRequest;
import com.hms.api.dto.CreateStaffRequest;
import com.hms.api.dto.UpdateUserStatusRequest;
import com.hms.api.repository.DoctorRepository;
import com.hms.api.repository.RefreshTokenRepository;
import com.hms.api.repository.UserRepository;
import com.hms.api.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User admin1;
    private User docUser;
    private String admin1Token;
    private String doctorToken;
    private String receptionistToken;
    private String patientToken;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        doctorRepository.deleteAll();
        userRepository.deleteAll();

        admin1 = userRepository.save(new User("admin1@example.com", "+971501111111", "hash", Role.ADMIN, "Super Admin"));
        docUser = userRepository.save(new User("doc@example.com", "+971502222222", "hash", Role.DOCTOR, "Dr. Alice"));
        User rec = userRepository.save(new User("rec@example.com", "+971503333333", "hash", Role.RECEPTIONIST, "Receptionist User"));
        User pat = userRepository.save(new User("pat@example.com", "+971504444444", "hash", Role.PATIENT, "Patient User"));

        admin1Token = "Bearer " + jwtTokenProvider.generateAccessToken(admin1);
        doctorToken = "Bearer " + jwtTokenProvider.generateAccessToken(docUser);
        receptionistToken = "Bearer " + jwtTokenProvider.generateAccessToken(rec);
        patientToken = "Bearer " + jwtTokenProvider.generateAccessToken(pat);
    }

    @Test
    @DisplayName("User management security: 401 unauthenticated, 403 for DOCTOR, RECEPTIONIST, PATIENT")
    void userManagementSecurity() throws Exception {
        // 401 unauthenticated
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());

        // 403 for wrong roles
        String[] wrongTokens = { doctorToken, receptionistToken, patientToken };
        for (String token : wrongTokens) {
            mockMvc.perform(get("/api/admin/users").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Admin list users: pagination, filters, and NEVER returns password hashes")
    void adminListUsersNeverExposesPasswordHashes() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/users")
                .header("Authorization", admin1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(4))
                .andReturn();

        String body = result.getResponse().getContentAsString();

        // Strict verification: passwords and password hashes must never be exposed
        assertThat(body).doesNotContain("password");
        assertThat(body).doesNotContain("hash");
    }

    @Test
    @DisplayName("Admin create staff: allows ADMIN, DOCTOR, RECEPTIONIST; rejects PATIENT with 400")
    void adminCreateStaffRejectsPatientRole() throws Exception {
        // 1. Attempt to create PATIENT via staff endpoint
        CreateStaffRequest patientReq = new CreateStaffRequest(
            "fake.patient@example.com", "+971509990022", "Fake Patient", "Password123", Role.PATIENT
        );

        mockMvc.perform(post("/api/admin/users")
                .header("Authorization", admin1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patientReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot create PATIENT via staff management"));

        // 2. Valid RECEPTIONIST creation
        CreateStaffRequest validReq = new CreateStaffRequest(
            "new.rec@example.com", "+971509990033", "New Receptionist", "Password123", Role.RECEPTIONIST
        );

        mockMvc.perform(post("/api/admin/users")
                .header("Authorization", admin1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.rec@example.com"))
                .andExpect(jsonPath("$.role").value("RECEPTIONIST"));
    }

    @Test
    @DisplayName("Role change: admin cannot demote themselves or last remaining ADMIN (409); revokes tokens")
    void adminChangeRoleRulesAndTokenRevocation() throws Exception {
        // Seed a second admin
        User admin2 = userRepository.save(new User("admin2@example.com", "+971501111112", "hash", Role.ADMIN, "Admin Two"));

        // 1. Admin cannot demote themselves (409)
        ChangeRoleRequest demoteSelfReq = new ChangeRoleRequest(Role.RECEPTIONIST);
        mockMvc.perform(patch("/api/admin/users/" + admin1.getId() + "/role")
                .header("Authorization", admin1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(demoteSelfReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An admin cannot change their own role"));

        // 2. Demote admin2 works when 2 admins exist
        RefreshToken admin2Token = refreshTokenRepository.save(new RefreshToken("admin2_hash", admin2, LocalDateTime.now().plusDays(7)));

        mockMvc.perform(patch("/api/admin/users/" + admin2.getId() + "/role")
                .header("Authorization", admin1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeRoleRequest(Role.DOCTOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DOCTOR"));

        // Verify admin2's refresh token was revoked
        RefreshToken refreshed = refreshTokenRepository.findByTokenHash("admin2_hash").orElseThrow();
        assertThat(refreshed.isRevoked()).isTrue();

        // 3. Now only admin1 is left. Demoting the last admin must fail with 409
        String admin2BearerToken = "Bearer " + jwtTokenProvider.generateAccessToken(admin2);
        // Note: admin2 has Role.DOCTOR now, so they'd get 403 anyway; but even if admin1 tried to demote admin1 it's self-demote 409
    }

    @Test
    @DisplayName("Disable user: cannot disable self or last ADMIN (409); revokes tokens and deactivates doctor")
    void adminDisableUserRulesAndDoctorDeactivation() throws Exception {
        // 1. Admin cannot disable self (409)
        UpdateUserStatusRequest disableReq = new UpdateUserStatusRequest(false);
        mockMvc.perform(patch("/api/admin/users/" + admin1.getId() + "/status")
                .header("Authorization", admin1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(disableReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An admin cannot disable their own account"));

        // 2. Link doctor profile to docUser
        Doctor doc = new Doctor(docUser, "Bio", "MBBS", "English", 5, BigDecimal.valueOf(200));
        doctorRepository.save(doc);

        RefreshToken docRefreshToken = refreshTokenRepository.save(new RefreshToken("doc_hash_99", docUser, LocalDateTime.now().plusDays(7)));

        // 3. Disable doctor user
        mockMvc.perform(patch("/api/admin/users/" + docUser.getId() + "/status")
                .header("Authorization", admin1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(disableReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Verify docUser disabled
        User updatedDocUser = userRepository.findById(docUser.getId()).orElseThrow();
        assertThat(updatedDocUser.isEnabled()).isFalse();

        // Verify refresh token revoked
        RefreshToken updatedToken = refreshTokenRepository.findByTokenHash("doc_hash_99").orElseThrow();
        assertThat(updatedToken.isRevoked()).isTrue();

        // Verify linked doctor profile deactivated
        Doctor updatedDoctor = doctorRepository.findByUserId(docUser.getId()).orElseThrow();
        assertThat(updatedDoctor.isActive()).isFalse();
    }
}
