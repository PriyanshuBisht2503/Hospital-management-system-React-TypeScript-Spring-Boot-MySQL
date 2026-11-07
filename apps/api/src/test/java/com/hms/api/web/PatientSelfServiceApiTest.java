package com.hms.api.web;

import com.hms.api.domain.*;
import com.hms.api.dto.AppointmentResponseDto;
import com.hms.api.repository.*;
import com.hms.api.security.JwtTokenProvider;
import com.hms.api.service.AppointmentBookingService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientSelfServiceApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private DoctorScheduleRepository scheduleRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AppointmentBookingService bookingService;
    @Autowired private JwtTokenProvider tokenProvider;

    private User patientUser1;
    private User patientUser2;
    private Patient patient1;
    private Patient patient2;
    private Doctor doctor;
    private Branch branch;
    private String patient1Token;
    private String patient2Token;
    private String staffToken;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        scheduleRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();

        patientUser1 = userRepository.save(new User("ss.p1@example.com", "+919100000001", null, Role.PATIENT, "Patient One"));
        patientUser2 = userRepository.save(new User("ss.p2@example.com", "+919100000002", null, Role.PATIENT, "Patient Two"));
        User staffUser = userRepository.save(new User("ss.staff@example.com", "+919100000003", "pass", Role.RECEPTIONIST, "Staff"));
        User docUser = userRepository.save(new User("ss.doc@example.com", "+919100000004", "pass", Role.DOCTOR, "Dr SS"));

        branch = branchRepository.save(new Branch("SS Branch", "SSBR", "Road", "Dubai", "+97140000000", "ss@example.com"));
        doctor = doctorRepository.save(new Doctor(docUser, "bio", "MBBS", "English", 10, new BigDecimal("100.00")));

        patient1 = new Patient("Patient One", "+919100000001", "ss.p1@example.com", LocalDate.of(1990, 1, 1), Gender.MALE);
        patient1.setUser(patientUser1);
        patient1 = patientRepository.save(patient1);

        patient2 = new Patient("Patient Two", "+919100000002", "ss.p2@example.com", LocalDate.of(1991, 2, 2), Gender.FEMALE);
        patient2.setUser(patientUser2);
        patient2 = patientRepository.save(patient2);

        patient1Token = "Bearer " + tokenProvider.generateAccessToken(patientUser1);
        patient2Token = "Bearer " + tokenProvider.generateAccessToken(patientUser2);
        staffToken = "Bearer " + tokenProvider.generateAccessToken(staffUser);

        for (DayOfWeek dow : DayOfWeek.values()) {
            scheduleRepository.save(new DoctorSchedule(doctor, branch, dow, LocalTime.of(9, 0), LocalTime.of(18, 0), 30, LocalDate.now().minusDays(10)));
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        Thread.sleep(100);
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        scheduleRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Instant future(int days, int hour) {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(days);
        return LocalDateTime.of(date, LocalTime.of(hour, 0)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
    }

    private AppointmentResponseDto book(Patient p, int days, int hour) {
        Instant start = future(days, hour);
        return bookingService.bookAppointment(p.getId(), doctor.getId(), branch.getId(), start,
                start.plus(30, ChronoUnit.MINUTES), AppointmentType.IN_PERSON, "checkup", Role.RECEPTIONIST, "staff");
    }

    private Appointment saveWithStatus(Patient p, Instant start, AppointmentStatus status) {
        Doctor d = doctorRepository.findById(doctor.getId()).orElseThrow();
        Appointment a = new Appointment(p, d, branch, start, start.plus(30, ChronoUnit.MINUTES), AppointmentType.IN_PERSON, status);
        a.setCreatedBy("staff");
        return appointmentRepository.save(a);
    }

    // ------------------------------------------------------------------ /api/auth/me

    @Test
    @DisplayName("GET /api/auth/me exposes patientId for a PATIENT")
    void meExposesPatientIdForPatient() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", patient1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.patientId").value(patient1.getId()))
                .andExpect(jsonPath("$.doctorId").value(nullValue()));
    }

    // ------------------------------------------------------------------ /api/appointments/me

    @Test
    @DisplayName("GET /api/appointments/me returns only the caller's appointments, newest first")
    void myAppointmentsReturnsOnlyOwn() throws Exception {
        book(patient1, 2, 10);
        book(patient1, 3, 11);
        book(patient2, 4, 12);

        mockMvc.perform(get("/api/appointments/me").header("Authorization", patient1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].patient.id").value(patient1.getId()))
                .andExpect(jsonPath("$.content[1].patient.id").value(patient1.getId()));
    }

    @Test
    @DisplayName("GET /api/appointments/me?upcoming=true excludes past appointments")
    void myAppointmentsUpcomingFilter() throws Exception {
        Instant past = Instant.now().minus(2, ChronoUnit.DAYS);
        saveWithStatus(patient1, past, AppointmentStatus.COMPLETED);
        book(patient1, 2, 10);

        mockMvc.perform(get("/api/appointments/me").param("upcoming", "true").header("Authorization", patient1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/appointments/me is forbidden for staff")
    void myAppointmentsForbiddenForStaff() throws Exception {
        mockMvc.perform(get("/api/appointments/me").header("Authorization", staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/appointments/me requires authentication")
    void myAppointmentsRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/appointments/me")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ /api/patients/me

    @Test
    @DisplayName("GET /api/patients/me returns the caller's own profile")
    void getMyProfileReturnsSelf() throws Exception {
        mockMvc.perform(get("/api/patients/me").header("Authorization", patient1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient1.getId()))
                .andExpect(jsonPath("$.email").value("ss.p1@example.com"));
    }

    @Test
    @DisplayName("GET /api/patients/me is forbidden for staff")
    void getMyProfileForbiddenForStaff() throws Exception {
        mockMvc.perform(get("/api/patients/me").header("Authorization", staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/patients/me updates allowed fields and keeps email immutable")
    void updateMyProfileKeepsEmail() throws Exception {
        String body = """
                { "fullName": "Patient One Renamed", "phone": "+919100000099", "dateOfBirth": "1990-05-05", "gender": "MALE" }
                """;

        mockMvc.perform(put("/api/patients/me").header("Authorization", patient1Token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Patient One Renamed"))
                .andExpect(jsonPath("$.phone").value("+919100000099"))
                .andExpect(jsonPath("$.email").value("ss.p1@example.com"));

        Patient reloaded = patientRepository.findById(patient1.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Patient One Renamed");
    }

    @Test
    @DisplayName("PUT /api/patients/me rejects a blank full name")
    void updateMyProfileRejectsBlankName() throws Exception {
        String body = """
                { "fullName": "   " }
                """;

        mockMvc.perform(put("/api/patients/me").header("Authorization", patient1Token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ patient cancel

    @Test
    @DisplayName("PATCH /api/appointments/{id}/cancel requires a reason")
    void cancelRequiresReason() throws Exception {
        AppointmentResponseDto dto = book(patient1, 2, 10);

        mockMvc.perform(patch("/api/appointments/" + dto.id() + "/cancel")
                        .header("Authorization", patient1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A patient cannot cancel another patient's appointment (404)")
    void cancelOtherPatientAppointmentReturns404() throws Exception {
        AppointmentResponseDto dto = book(patient1, 2, 10);

        mockMvc.perform(patch("/api/appointments/" + dto.id() + "/cancel")
                        .header("Authorization", patient2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Not mine\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A patient can only cancel BOOKED or CONFIRMED appointments")
    void cancelRejectsCheckedInForPatient() throws Exception {
        Appointment a = saveWithStatus(patient1, future(2, 10), AppointmentStatus.CHECKED_IN);

        mockMvc.perform(patch("/api/appointments/" + a.getId() + "/cancel")
                        .header("Authorization", patient1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Changed plans\"}"))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ patient reschedule

    @Test
    @DisplayName("A patient cannot reschedule another patient's appointment (404)")
    void rescheduleOtherPatientAppointmentReturns404() throws Exception {
        AppointmentResponseDto dto = book(patient1, 2, 10);
        Instant newStart = future(5, 10);

        String body = String.format("{\"newStartTime\":\"%s\",\"newEndTime\":\"%s\"}", newStart, newStart.plus(30, ChronoUnit.MINUTES));

        mockMvc.perform(post("/api/appointments/" + dto.id() + "/reschedule")
                        .header("Authorization", patient2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ extra coverage

    @Test
    @DisplayName("GET /api/appointments/me is routed to the collection, not parsed as an id")
    void myAppointmentsMeIsNotParsedAsId() throws Exception {
        book(patient1, 2, 10);

        mockMvc.perform(get("/api/appointments/me").header("Authorization", patient1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(6))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/auth/me exposes a null patientId for staff")
    void meExposesNullPatientIdForStaff() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(nullValue()));
    }

    @Test
    @DisplayName("PUT /api/patients/me ignores email and userId supplied in the body")
    void updateMyProfileCannotChangeEmailOrUserId() throws Exception {
        String body = """
                { "fullName": "Renamed Patient", "email": "attacker@example.com", "userId": 999 }
                """;

        mockMvc.perform(put("/api/patients/me").header("Authorization", patient1Token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ss.p1@example.com"))
                .andExpect(jsonPath("$.userId").value(patientUser1.getId()));
    }

    @Test
    @DisplayName("GET /api/patients/me returns 404 when the caller has no patient row")
    void getMyProfileWithoutPatientRowReturns404() throws Exception {
        User orphan = userRepository.save(new User("ss.orphan@example.com", "+919100000777", null, Role.PATIENT, "Orphan"));
        String orphanToken = "Bearer " + tokenProvider.generateAccessToken(orphan);

        mockMvc.perform(get("/api/patients/me").header("Authorization", orphanToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("GET /api/appointments/me caps the page size at 100")
    void myAppointmentsSizeCapIsEnforced() throws Exception {
        mockMvc.perform(get("/api/appointments/me").param("size", "500").header("Authorization", patient1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(100));
    }
}
