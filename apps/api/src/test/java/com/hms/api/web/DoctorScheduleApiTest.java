package com.hms.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.*;
import com.hms.api.dto.CreateDoctorScheduleRequest;
import com.hms.api.dto.UpdateDoctorScheduleRequest;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DoctorScheduleApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DoctorScheduleRepository scheduleRepository;

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
    private User receptionistUser;
    private User patientUser;

    private Doctor doctor1;
    private Doctor doctor2;
    private Branch branch;

    private String adminToken;
    private String doctor1Token;
    private String doctor2Token;
    private String receptionistToken;
    private String patientToken;

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        scheduleRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = userRepository.save(new User("admin.sched@example.com", "+919300000001", "pass", Role.ADMIN, "Admin Sched"));
        doctorUser1 = userRepository.save(new User("doc1.sched@example.com", "+919300000002", "pass", Role.DOCTOR, "Dr. One"));
        doctorUser2 = userRepository.save(new User("doc2.sched@example.com", "+919300000003", "pass", Role.DOCTOR, "Dr. Two"));
        receptionistUser = userRepository.save(new User("rec.sched@example.com", "+919300000004", "pass", Role.RECEPTIONIST, "Receptionist"));
        patientUser = userRepository.save(new User("pat.sched@example.com", "+919300000005", "pass", Role.PATIENT, "Patient Sched"));

        branch = branchRepository.save(new Branch("City Branch", "CITY01", "Main Rd", "City", "+91900111222", "city@example.com"));

        doctor1 = doctorRepository.save(new Doctor(doctorUser1, "Bio 1", "MD", "English", 10, new BigDecimal("600.00")));
        doctor2 = doctorRepository.save(new Doctor(doctorUser2, "Bio 2", "MBBS", "Hindi", 5, new BigDecimal("400.00")));

        adminToken = "Bearer " + tokenProvider.generateAccessToken(adminUser);
        doctor1Token = "Bearer " + tokenProvider.generateAccessToken(doctorUser1);
        doctor2Token = "Bearer " + tokenProvider.generateAccessToken(doctorUser2);
        receptionistToken = "Bearer " + tokenProvider.generateAccessToken(receptionistUser);
        patientToken = "Bearer " + tokenProvider.generateAccessToken(patientUser);
    }

    @AfterEach
    void tearDown() {
        appointmentRepository.deleteAll();
        scheduleRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Admin creates doctor schedule template successfully")
    void adminCreatesScheduleSuccessfully() throws Exception {
        CreateDoctorScheduleRequest req = new CreateDoctorScheduleRequest(
                branch.getId(),
                DayOfWeek.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                30,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                LocalDate.now().minusDays(1),
                null
        );

        mockMvc.perform(post("/api/admin/doctors/" + doctor1.getId() + "/schedules")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doctorId").value(doctor1.getId()))
                .andExpect(jsonPath("$.dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.slotMinutes").value(30))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("Overlapping schedule on same day of week is rejected with 409 Conflict")
    void overlappingScheduleRejected() throws Exception {
        DoctorSchedule sched1 = new DoctorSchedule(
                doctor1, branch, DayOfWeek.TUESDAY,
                LocalTime.of(9, 0), LocalTime.of(13, 0),
                30, LocalDate.now().minusDays(5)
        );
        scheduleRepository.save(sched1);

        CreateDoctorScheduleRequest overlapReq = new CreateDoctorScheduleRequest(
                branch.getId(),
                DayOfWeek.TUESDAY,
                LocalTime.of(12, 0),
                LocalTime.of(16, 0),
                30,
                null, null,
                LocalDate.now().minusDays(1),
                null
        );

        mockMvc.perform(post("/api/admin/doctors/" + doctor1.getId() + "/schedules")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overlapReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Schedule with invalid break window is rejected with 400 Bad Request")
    void invalidBreakWindowRejected() throws Exception {
        CreateDoctorScheduleRequest req = new CreateDoctorScheduleRequest(
                branch.getId(),
                DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                30,
                LocalTime.of(14, 0), // breakStart after breakEnd
                LocalTime.of(13, 0),
                LocalDate.now(),
                null
        );

        mockMvc.perform(post("/api/admin/doctors/" + doctor1.getId() + "/schedules")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Doctor can view their own schedules, but not other doctors")
    void doctorScheduleAccessControl() throws Exception {
        mockMvc.perform(get("/api/admin/doctors/" + doctor1.getId() + "/schedules")
                        .header("Authorization", doctor1Token))
                .andExpect(status().isOk());

        // Doctor 2 accessing Doctor 1's schedule -> 403
        mockMvc.perform(get("/api/admin/doctors/" + doctor1.getId() + "/schedules")
                        .header("Authorization", doctor2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Receptionist can view schedules, but cannot create (Admin only)")
    void receptionistAccessControl() throws Exception {
        mockMvc.perform(get("/api/admin/doctors/" + doctor1.getId() + "/schedules")
                        .header("Authorization", receptionistToken))
                .andExpect(status().isOk());

        CreateDoctorScheduleRequest req = new CreateDoctorScheduleRequest(
                branch.getId(), DayOfWeek.FRIDAY,
                LocalTime.of(10, 0), LocalTime.of(16, 0),
                30, null, null, LocalDate.now(), null
        );

        mockMvc.perform(post("/api/admin/doctors/" + doctor1.getId() + "/schedules")
                        .header("Authorization", receptionistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Patient cannot access admin schedule endpoints")
    void patientAccessControl() throws Exception {
        mockMvc.perform(get("/api/admin/doctors/" + doctor1.getId() + "/schedules")
                        .header("Authorization", patientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deactivation rejected with 409 Conflict if doctor has future active appointments")
    void deactivationWithFutureAppointmentsRejected() throws Exception {
        DoctorSchedule sched = new DoctorSchedule(
                doctor1, branch, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0),
                30, LocalDate.now().minusDays(5)
        );
        sched = scheduleRepository.save(sched);

        // Create future active appointment for doctor1 on a Monday
        Patient patient = patientRepository.save(new Patient("Pat", "+919300000099", "pat@example.com", null, Gender.MALE));
        LocalDate nextMonday = LocalDate.now().plusWeeks(1).with(DayOfWeek.MONDAY);
        Instant apptStart = LocalDateTime.of(nextMonday, LocalTime.of(10, 0)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant apptEnd = apptStart.plus(30, ChronoUnit.MINUTES);

        Appointment appt = new Appointment(patient, doctor1, branch, apptStart, apptEnd, AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        appointmentRepository.save(appt);

        // Try to deactivate
        mockMvc.perform(delete("/api/admin/doctors/" + doctor1.getId() + "/schedules/" + sched.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isConflict());
    }
}

