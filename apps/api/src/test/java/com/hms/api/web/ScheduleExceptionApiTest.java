package com.hms.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.*;
import com.hms.api.dto.CreateScheduleExceptionRequest;
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
import java.time.*;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScheduleExceptionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ScheduleExceptionRepository exceptionRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private User adminUser;
    private User doctorUser1;
    private User doctorUser2;
    private Doctor doctor1;
    private Doctor doctor2;
    private Branch branch;
    private Patient patient;

    private String adminToken;
    private String doctor1Token;
    private String doctor2Token;

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        exceptionRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = userRepository.save(new User("admin.ex@example.com", "+919400000001", "pass", Role.ADMIN, "Admin Ex"));
        doctorUser1 = userRepository.save(new User("doc1.ex@example.com", "+919400000002", "pass", Role.DOCTOR, "Dr. Ex One"));
        doctorUser2 = userRepository.save(new User("doc2.ex@example.com", "+919400000003", "pass", Role.DOCTOR, "Dr. Ex Two"));

        branch = branchRepository.save(new Branch("East Clinic", "EAST01", "East Rd", "City", "+91900111333", "east@example.com"));

        doctor1 = doctorRepository.save(new Doctor(doctorUser1, "Bio", "MD", "English", 8, new BigDecimal("550.00")));
        doctor2 = doctorRepository.save(new Doctor(doctorUser2, "Bio", "MBBS", "Hindi", 4, new BigDecimal("350.00")));

        patient = patientRepository.save(new Patient("Ex Patient", "+919400000099", "expat@example.com", null, Gender.FEMALE));

        adminToken = "Bearer " + tokenProvider.generateAccessToken(adminUser);
        doctor1Token = "Bearer " + tokenProvider.generateAccessToken(doctorUser1);
        doctor2Token = "Bearer " + tokenProvider.generateAccessToken(doctorUser2);
    }

    @AfterEach
    void tearDown() {
        appointmentRepository.deleteAll();
        exceptionRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Doctor creates leave exception with no appointments successfully")
    void doctorCreatesLeaveSuccessfully() throws Exception {
        LocalDate leaveDate = LocalDate.now().plusDays(5);
        CreateScheduleExceptionRequest req = new CreateScheduleExceptionRequest(
                leaveDate, null, null, ScheduleExceptionType.LEAVE, "Annual Vacation", false
        );

        mockMvc.perform(post("/api/admin/doctors/" + doctor1.getId() + "/exceptions")
                        .header("Authorization", doctor1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doctorId").value(doctor1.getId()))
                .andExpect(jsonPath("$.type").value("LEAVE"))
                .andExpect(jsonPath("$.date").value(leaveDate.toString()));
    }

    @Test
    @DisplayName("Leave overlapping appointment with force=false returns 409 Conflict with affected IDs")
    void leaveWithAppointmentConflictReturns409() throws Exception {
        LocalDate leaveDate = LocalDate.now().plusDays(4);
        Instant apptStart = LocalDateTime.of(leaveDate, LocalTime.of(10, 0)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant apptEnd = apptStart.plus(30, ChronoUnit.MINUTES);

        Appointment appt = new Appointment(patient, doctor1, branch, apptStart, apptEnd, AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        appt = appointmentRepository.save(appt);

        CreateScheduleExceptionRequest req = new CreateScheduleExceptionRequest(
                leaveDate, null, null, ScheduleExceptionType.LEAVE, "Sudden Leave", false
        );

        mockMvc.perform(post("/api/admin/doctors/" + doctor1.getId() + "/exceptions")
                        .header("Authorization", doctor1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString(appt.getId().toString())));
    }

    @Test
    @DisplayName("Leave overlapping appointment with force=true sets needs_rescheduling=true without cancelling")
    void leaveWithForceTrueFlagsNeedsRescheduling() throws Exception {
        LocalDate leaveDate = LocalDate.now().plusDays(4);
        Instant apptStart = LocalDateTime.of(leaveDate, LocalTime.of(11, 0)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant apptEnd = apptStart.plus(30, ChronoUnit.MINUTES);

        Appointment appt = new Appointment(patient, doctor1, branch, apptStart, apptEnd, AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        appt = appointmentRepository.save(appt);
        assertThat(appt.isNeedsRescheduling()).isFalse();

        CreateScheduleExceptionRequest req = new CreateScheduleExceptionRequest(
                leaveDate, null, null, ScheduleExceptionType.LEAVE, "Emergency Leave", true
        );

        mockMvc.perform(post("/api/admin/doctors/" + doctor1.getId() + "/exceptions")
                        .header("Authorization", doctor1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Verify appointment was NOT cancelled, but flagged needs_rescheduling=true
        Appointment updatedAppt = appointmentRepository.findById(appt.getId()).orElseThrow();
        assertThat(updatedAppt.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(updatedAppt.isNeedsRescheduling()).isTrue();
    }

    @Test
    @DisplayName("Doctor cannot create or view exceptions for another doctor")
    void doctorExceptionAccessControl() throws Exception {
        LocalDate date = LocalDate.now().plusDays(6);
        CreateScheduleExceptionRequest req = new CreateScheduleExceptionRequest(
                date, null, null, ScheduleExceptionType.LEAVE, "Personal", false
        );

        // Doctor 2 attempts to add leave for Doctor 1 -> 403 Forbidden
        mockMvc.perform(post("/api/admin/doctors/" + doctor1.getId() + "/exceptions")
                        .header("Authorization", doctor2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // Doctor 2 attempts to view exceptions for Doctor 1 -> 403 Forbidden
        mockMvc.perform(get("/api/admin/doctors/" + doctor1.getId() + "/exceptions")
                        .header("Authorization", doctor2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Doctor deletes exception successfully")
    void doctorDeletesException() throws Exception {
        ScheduleException ex = new ScheduleException(doctor1, LocalDate.now().plusDays(8), ScheduleExceptionType.LEAVE, "Off day");
        ex = exceptionRepository.save(ex);

        mockMvc.perform(delete("/api/admin/doctors/" + doctor1.getId() + "/exceptions/" + ex.getId())
                        .header("Authorization", doctor1Token))
                .andExpect(status().isNoContent());

        assertThat(exceptionRepository.findById(ex.getId())).isEmpty();
    }
}

