package com.hms.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.*;
import com.hms.api.dto.DoctorCreateRequest;
import com.hms.api.dto.DoctorUpdateRequest;
import com.hms.api.repository.*;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DoctorManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DoctorScheduleRepository doctorScheduleRepository;

    @Autowired
    private ScheduleExceptionRepository scheduleExceptionRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String doctorToken;
    private String receptionistToken;
    private String patientToken;

    private Branch branch;
    private Specialty specialty;

    @BeforeEach
    void setUp() {
        cleanAll();

        branch = branchRepository.save(new Branch("Mankhool Hospital", "MANK-01", "Al Mankhool", "Dubai", "043940000", "mankhool@example.com"));
        specialty = specialtyRepository.save(new Specialty("Cardiology", "CARD-01", "Heart specialty"));

        User admin = userRepository.save(new User("admin@example.com", "+971501111111", "hash", Role.ADMIN, "Admin User"));
        User doc = userRepository.save(new User("doc@example.com", "+971502222222", "hash", Role.DOCTOR, "Dr. Alice"));
        User rec = userRepository.save(new User("rec@example.com", "+971503333333", "hash", Role.RECEPTIONIST, "Receptionist User"));
        User pat = userRepository.save(new User("pat@example.com", "+971504444444", "hash", Role.PATIENT, "Patient User"));

        adminToken = "Bearer " + jwtTokenProvider.generateAccessToken(admin);
        doctorToken = "Bearer " + jwtTokenProvider.generateAccessToken(doc);
        receptionistToken = "Bearer " + jwtTokenProvider.generateAccessToken(rec);
        patientToken = "Bearer " + jwtTokenProvider.generateAccessToken(pat);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        refreshTokenRepository.deleteAll();
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        scheduleExceptionRepository.deleteAll();
        doctorScheduleRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();
        branchRepository.deleteAll();
        specialtyRepository.deleteAll();
    }

    @Test
    @DisplayName("Public doctor list and detail: returns active only, with NO email, phone, or userId")
    void publicDoctorListAndDetailContainNoPrivateFields() throws Exception {
        User docUser = userRepository.save(new User("dr.private@example.com", "+971508889900", "hash", Role.DOCTOR, "Dr. Private Specialist"));
        Doctor doc = new Doctor(docUser, "Expert Cardiologist", "MBBS, MD", "English, Arabic", 12, BigDecimal.valueOf(350));
        doc.setBranches(Set.of(branch));
        doc.setSpecialties(Set.of(specialty));
        doc.setPhotoUrl("https://storage.hms.local/photos/dr-private.jpg");
        doc = doctorRepository.save(doc);

        // Inactive doctor that must NOT appear publicly
        User inactiveUser = userRepository.save(new User("dr.inactive@example.com", "+971507778899", "hash", Role.DOCTOR, "Dr. Inactive"));
        Doctor inactiveDoc = new Doctor(inactiveUser, "Inactive", "MBBS", "English", 2, BigDecimal.valueOf(100));
        inactiveDoc.setActive(false);
        doctorRepository.save(inactiveDoc);

        // 1. GET /api/doctors (public list)
        MvcResult listResult = mockMvc.perform(get("/api/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Dr. Private Specialist"))
                .andReturn();

        String listJson = listResult.getResponse().getContentAsString();
        JsonNode firstDoc = objectMapper.readTree(listJson).get("content").get(0);

        // Verify strictly NO private fields
        assertThat(firstDoc.has("email")).isFalse();
        assertThat(firstDoc.has("phone")).isFalse();
        assertThat(firstDoc.has("userId")).isFalse();
        assertThat(firstDoc.has("user")).isFalse();
        assertThat(listJson).doesNotContain("dr.private@example.com");
        assertThat(listJson).doesNotContain("+971508889900");

        // 2. GET /api/doctors/{id} (public detail)
        MvcResult detailResult = mockMvc.perform(get("/api/doctors/" + doc.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Dr. Private Specialist"))
                .andExpect(jsonPath("$.bio").value("Expert Cardiologist"))
                .andExpect(jsonPath("$.consultationFee").value(350.0))
                .andReturn();

        String detailJson = detailResult.getResponse().getContentAsString();
        JsonNode detailNode = objectMapper.readTree(detailJson);

        assertThat(detailNode.has("email")).isFalse();
        assertThat(detailNode.has("phone")).isFalse();
        assertThat(detailNode.has("userId")).isFalse();
        assertThat(detailNode.has("user")).isFalse();
        assertThat(detailJson).doesNotContain("dr.private@example.com");
        assertThat(detailJson).doesNotContain("+971508889900");

        // Inactive doctor returns 404 from public endpoint
        mockMvc.perform(get("/api/doctors/" + inactiveDoc.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Admin doctor endpoints: 401 unauthenticated, 403 wrong roles")
    void adminDoctorSecurityChecks() throws Exception {
        DoctorCreateRequest req = new DoctorCreateRequest(
            "newdoc@example.com", "+971505556677", "Dr. Bob", "InitialPassword123",
            "Bio", "MD", "English", 5, "photo.jpg", BigDecimal.valueOf(250),
            Set.of(specialty.getId()), Set.of(branch.getId())
        );
        String json = objectMapper.writeValueAsString(req);

        // 401 unauthenticated
        mockMvc.perform(post("/api/admin/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isUnauthorized());

        // 403 for wrong roles
        String[] wrongTokens = { doctorToken, receptionistToken, patientToken };
        for (String token : wrongTokens) {
            mockMvc.perform(post("/api/admin/doctors")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Admin create doctor: ONE transaction creates DOCTOR user and profile, enforces min 8 char password")
    void adminCreateDoctorTransactionAndValidation() throws Exception {
        // 1. Password < 8 characters rejected
        DoctorCreateRequest shortPassReq = new DoctorCreateRequest(
            "short@example.com", "+971505556600", "Dr. Short", "1234567",
            "Bio", "MD", "English", 5, "photo.jpg", BigDecimal.valueOf(250),
            Set.of(specialty.getId()), Set.of(branch.getId())
        );

        mockMvc.perform(post("/api/admin/doctors")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shortPassReq)))
                .andExpect(status().isBadRequest());

        // 2. Valid create: creates User and Doctor profile together
        DoctorCreateRequest validReq = new DoctorCreateRequest(
            "dr.john@example.com", "+971505556611", "Dr. John Doe", "InitialPassword123",
            "Cardiologist bio", "MBBS, FACC", "English, Hindi", 10, "photo.jpg", BigDecimal.valueOf(300),
            Set.of(specialty.getId()), Set.of(branch.getId())
        );

        String res = mockMvc.perform(post("/api/admin/doctors")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("dr.john@example.com"))
                .andExpect(jsonPath("$.fullName").value("Dr. John Doe"))
                .andReturn().getResponse().getContentAsString();

        Long doctorId = objectMapper.readTree(res).get("id").asLong();

        // Verify User was created in DB with Role.DOCTOR
        User createdUser = userRepository.findByEmail("dr.john@example.com").orElseThrow();
        assertThat(createdUser.getRole()).isEqualTo(Role.DOCTOR);
        assertThat(createdUser.isEnabled()).isTrue();

        // Verify Doctor in DB is linked to the user
        Doctor createdDoctor = doctorRepository.findById(doctorId).orElseThrow();
        assertThat(createdDoctor.getUser().getId()).isEqualTo(createdUser.getId());
        assertThat(createdDoctor.isActive()).isTrue();
    }

    @Test
    @DisplayName("Admin deactivate doctor: disables linked user and revokes refresh tokens")
    void adminDeactivateDoctorDisablesUserAndRevokesTokens() throws Exception {
        // Create doctor user and doctor profile
        User docUser = userRepository.save(new User("dr.retire@example.com", "+971509990011", "pass", Role.DOCTOR, "Dr. Retire"));
        Doctor doc = new Doctor(docUser, "Bio", "MBBS", "English", 20, BigDecimal.valueOf(400));
        doc = doctorRepository.save(doc);

        // Seed an active refresh token for this doctor user
        RefreshToken token = new RefreshToken("token_hash_123", docUser, LocalDateTime.now().plusDays(7));
        refreshTokenRepository.save(token);

        // Deactivate doctor
        mockMvc.perform(delete("/api/admin/doctors/" + doc.getId())
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Doctor profile deactivated and linked user disabled"));

        // Verify doctor isActive = false
        Doctor updatedDoctor = doctorRepository.findById(doc.getId()).orElseThrow();
        assertThat(updatedDoctor.isActive()).isFalse();

        // Verify linked user enabled = false
        User updatedUser = userRepository.findById(docUser.getId()).orElseThrow();
        assertThat(updatedUser.isEnabled()).isFalse();

        // Verify refresh token was revoked
        RefreshToken updatedToken = refreshTokenRepository.findByTokenHash("token_hash_123").orElseThrow();
        assertThat(updatedToken.isRevoked()).isTrue();
    }
}
