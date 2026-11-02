package com.hms.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.*;
import com.hms.api.dto.CreatePatientRequest;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DoctorScheduleRepository scheduleRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private User adminUser;
    private User doctorUser;
    private User patientUser1;
    private User patientUser2;

    private Patient patient1;
    private Patient patient2;
    private Doctor doctor;
    private Branch branch;

    private String adminToken;
    private String doctorToken;
    private String patient1Token;
    private String patient2Token;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        scheduleRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();
        auditLogRepository.deleteAll();

        branch = branchRepository.save(new Branch("Apex Hospital", "APEX01", "Ring Rd", "Pune", "+912011112222", "apex@example.com"));

        adminUser = userRepository.save(new User("admin.pat@example.com", "+919100000001", "pass", Role.ADMIN, "Admin User"));
        doctorUser = userRepository.save(new User("doctor.pat@example.com", "+919100000002", "pass", Role.DOCTOR, "Dr. User"));
        doctor = doctorRepository.save(new Doctor(doctorUser, "Bio", "MBBS", "English", 5, new BigDecimal("500.00")));

        patientUser1 = userRepository.save(new User("p1.pat@example.com", "+919100000003", null, Role.PATIENT, "Patient One"));
        patientUser2 = userRepository.save(new User("p2.pat@example.com", "+919100000004", null, Role.PATIENT, "Patient Two"));

        patient1 = new Patient("Patient One", "+919100000003", "p1.pat@example.com", LocalDate.of(1990, 5, 20), Gender.MALE);
        patient1.setUser(patientUser1);
        patient1 = patientRepository.save(patient1);

        patient2 = new Patient("Patient Two", "+919100000004", "p2.pat@example.com", LocalDate.of(1985, 10, 15), Gender.FEMALE);
        patient2.setUser(patientUser2);
        patient2 = patientRepository.save(patient2);

        // Doctor has appointment with patient1 (granting clinical view to patient1, but NOT patient2)
        appointmentRepository.save(new Appointment(patient1, doctor, branch, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(5400), AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED));

        adminToken = "Bearer " + tokenProvider.generateAccessToken(adminUser);
        doctorToken = "Bearer " + tokenProvider.generateAccessToken(doctorUser);
        patient1Token = "Bearer " + tokenProvider.generateAccessToken(patientUser1);
        patient2Token = "Bearer " + tokenProvider.generateAccessToken(patientUser2);
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        scheduleRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/patients/{id} requires authentication (401 without token)")
    void getPatientAnonymous401() throws Exception {
        mockMvc.perform(get("/api/patients/" + patient1.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Patient cannot view another patient's profile: returns 404 identical to nonexistent patient")
    void patientCannotViewOtherPatient() throws Exception {
        // 1. Patient 1 views Patient 2 (another patient) -> 404 Not Found
        String otherPatientResponse = mockMvc.perform(get("/api/patients/" + patient2.getId())
                        .header("Authorization", patient1Token))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // 2. Patient 1 views Nonexistent patient -> 404 Not Found
        String nonexistentResponse = mockMvc.perform(get("/api/patients/999999")
                        .header("Authorization", patient1Token))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // 3. Both responses must be identical in structure and message (prevent enumeration)
        assertThat(objectMapper.readTree(otherPatientResponse).get("error").asText())
                .isEqualTo(objectMapper.readTree(nonexistentResponse).get("error").asText());
        assertThat(objectMapper.readTree(otherPatientResponse).get("status").asInt())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("Patient can view their own profile (200 OK)")
    void patientCanViewSelf() throws Exception {
        mockMvc.perform(get("/api/patients/" + patient1.getId())
                        .header("Authorization", patient1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient1.getId()))
                .andExpect(jsonPath("$.fullName").value("Patient One"))
                .andExpect(jsonPath("$.phone").value("+919100000003"))
                .andExpect(jsonPath("$.email").value("p1.pat@example.com"));
    }

    @Test
    @DisplayName("Doctor can view patient with appointment (masked contact); doctor without appointment receives 404")
    void doctorViewsPatientMaskedContact() throws Exception {
        // 1. Doctor has appointment with patient1 -> 200 OK with masked contact
        mockMvc.perform(get("/api/patients/" + patient1.getId())
                        .header("Authorization", doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient1.getId()))
                .andExpect(jsonPath("$.fullName").value("Patient One"))
                .andExpect(jsonPath("$.age").isNumber())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());

        // Verify audit log
        List<AuditLog> logs = auditLogRepository.findByAction("VIEW_PATIENT");
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getEntityId()).isEqualTo(String.valueOf(patient1.getId()));
        assertThat(logs.get(0).getDetails()).isNull();

        // 2. Doctor has NO appointment with patient2 -> 404 Not Found
        mockMvc.perform(get("/api/patients/" + patient2.getId())
                        .header("Authorization", doctorToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Admin views patient with full contact information")
    void adminViewsPatientFullContact() throws Exception {
        mockMvc.perform(get("/api/patients/" + patient1.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+919100000003"))
                .andExpect(jsonPath("$.email").value("p1.pat@example.com"));
    }

    @Test
    @DisplayName("Patient search: forbidden for patients, doctor restricted to appointed patients, admin sees all")
    void searchPatientAccessAndAudit() throws Exception {
        // Patient cannot search -> 403
        mockMvc.perform(get("/api/patients")
                        .header("Authorization", patient1Token))
                .andExpect(status().isForbidden());

        // Doctor search: returns ONLY patient1 (who has appointment with doctor), NOT patient2
        mockMvc.perform(get("/api/patients?search=Patient")
                        .header("Authorization", doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(patient1.getId()));

        // Admin search: returns both patient1 and patient2
        mockMvc.perform(get("/api/patients?search=Patient")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.pageSize").value(6));

        List<AuditLog> logs = auditLogRepository.findByAction("SEARCH_PATIENTS");
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getEntityId()).isEqualTo("Patient");
    }

    @Test
    @DisplayName("Create patient record: staff only (ADMIN, RECEPTIONIST)")
    void createPatientStaffOnly() throws Exception {
        CreatePatientRequest req = new CreatePatientRequest("New Walkin", "+919999888877", "walkin@example.com", LocalDate.of(1995, 3, 1), Gender.OTHER);

        mockMvc.perform(post("/api/patients")
                        .header("Authorization", patient1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/patients")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.fullName").value("New Walkin"));
    }
}
