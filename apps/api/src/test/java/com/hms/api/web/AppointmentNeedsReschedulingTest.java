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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppointmentNeedsReschedulingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DoctorScheduleRepository scheduleRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentBookingService bookingService;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private User adminUser;
    private User doctorUser1;
    private User doctorUser2;
    private User patientUser1;
    private User patientUser2;
    private Doctor doctor1;
    private Doctor doctor2;
    private Patient patient1;
    private Patient patient2;
    private Branch branch;
    private String adminToken;
    private String doctor1Token;
    private String patient1Token;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        scheduleRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = userRepository.save(new User("nr.admin@example.com", "+919300000001", "pass", Role.ADMIN, "Admin"));
        doctorUser1 = userRepository.save(new User("nr.doc1@example.com", "+919300000002", "pass", Role.DOCTOR, "Doctor One"));
        doctorUser2 = userRepository.save(new User("nr.doc2@example.com", "+919300000003", "pass", Role.DOCTOR, "Doctor Two"));
        patientUser1 = userRepository.save(new User("nr.pat1@example.com", "+919300000004", null, Role.PATIENT, "Patient One"));
        patientUser2 = userRepository.save(new User("nr.pat2@example.com", "+919300000005", null, Role.PATIENT, "Patient Two"));

        branch = branchRepository.save(new Branch("NR Hospital", "NR001", "Road", "Pune", "+912011112222", "nr@example.com"));
        doctor1 = doctorRepository.save(new Doctor(doctorUser1, "Spec", "MD", "English", 10, new BigDecimal("500.00")));
        doctor2 = doctorRepository.save(new Doctor(doctorUser2, "Spec2", "MD", "English", 10, new BigDecimal("500.00")));

        patient1 = new Patient("Patient One", "+919300000004", "nr.pat1@example.com", LocalDate.of(1990, 1, 1), Gender.MALE);
        patient1.setUser(patientUser1);
        patient1 = patientRepository.save(patient1);

        patient2 = new Patient("Patient Two", "+919300000005", "nr.pat2@example.com", LocalDate.of(1991, 2, 2), Gender.FEMALE);
        patient2.setUser(patientUser2);
        patient2 = patientRepository.save(patient2);

        adminToken = "Bearer " + tokenProvider.generateAccessToken(adminUser);
        doctor1Token = "Bearer " + tokenProvider.generateAccessToken(doctorUser1);
        patient1Token = "Bearer " + tokenProvider.generateAccessToken(patientUser1);

        for (DayOfWeek dow : DayOfWeek.values()) {
            scheduleRepository.save(new DoctorSchedule(doctor1, branch, dow, LocalTime.of(9, 0), LocalTime.of(18, 0), 30, LocalDate.now().minusDays(10)));
            scheduleRepository.save(new DoctorSchedule(doctor2, branch, dow, LocalTime.of(9, 0), LocalTime.of(18, 0), 30, LocalDate.now().minusDays(10)));
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        Thread.sleep(150);
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        scheduleRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Instant future(int days, int hour, int minute) {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(days);
        return LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
    }

    private void bookAndFlag(Patient p, Doctor d, boolean flag, int days, int hour, int minute) {
        Instant start = future(days, hour, minute);
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        AppointmentResponseDto dto = bookingService.bookAppointment(
                p.getId(), d.getId(), branch.getId(), start, end, AppointmentType.IN_PERSON, "reason", Role.ADMIN, "admin"
        );
        Appointment a = appointmentRepository.findById(dto.id()).orElseThrow();
        a.setNeedsRescheduling(flag);
        appointmentRepository.save(a);
    }

    @Test
    @DisplayName("ADMIN gets only flagged rows for true, only unflagged for false, all when omitted")
    void adminNeedsReschedulingFilter() throws Exception {
        bookAndFlag(patient1, doctor1, true, 2, 10, 0);
        bookAndFlag(patient2, doctor1, false, 2, 10, 30);

        mockMvc.perform(get("/api/appointments?needsRescheduling=true").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].needsRescheduling").value(true));

        mockMvc.perform(get("/api/appointments?needsRescheduling=false").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].needsRescheduling").value(false));

        mockMvc.perform(get("/api/appointments").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("DOCTOR sees only their own flagged appointments, never another doctor's")
    void doctorNeedsReschedulingScopedToOwn() throws Exception {
        bookAndFlag(patient1, doctor1, true, 2, 10, 0);
        bookAndFlag(patient1, doctor2, true, 2, 10, 30);

        mockMvc.perform(get("/api/appointments?needsRescheduling=true").header("Authorization", doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].doctorId").value(doctor1.getId()));
    }

    @Test
    @DisplayName("PATIENT sees only their own flagged appointments")
    void patientNeedsReschedulingScopedToOwn() throws Exception {
        bookAndFlag(patient1, doctor1, true, 2, 10, 0);
        bookAndFlag(patient2, doctor1, true, 2, 10, 30);

        mockMvc.perform(get("/api/appointments?needsRescheduling=true").header("Authorization", patient1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].patient.id").value(patient1.getId()));
    }
}
