package com.hms.api.web;

import com.hms.api.domain.*;
import com.hms.api.repository.*;
import com.hms.api.security.JwtTokenProvider;
import com.hms.api.security.RateLimiterService;
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

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.rate-limit.booking-per-minute=20")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingRateLimitTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RateLimiterService rateLimiterService;
    @Autowired private UserRepository userRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private DoctorScheduleRepository scheduleRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JwtTokenProvider tokenProvider;

    private Patient patient1;
    private Patient patient2;
    private Doctor doctor;
    private Branch branch;
    private String token1;
    private String token2;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        scheduleRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();
        rateLimiterService.clear();

        User u1 = userRepository.save(new User("rl.p1@example.com", "+919200000001", null, Role.PATIENT, "RL One"));
        User u2 = userRepository.save(new User("rl.p2@example.com", "+919200000002", null, Role.PATIENT, "RL Two"));
        User du = userRepository.save(new User("rl.doc@example.com", "+919200000003", "pass", Role.DOCTOR, "Dr RL"));

        branch = branchRepository.save(new Branch("RL Branch", "RLBR", "Road", "Dubai", "+97140000011", "rl@example.com"));
        doctor = doctorRepository.save(new Doctor(du, "bio", "MBBS", "English", 8, new BigDecimal("90.00")));

        patient1 = new Patient("RL One", "+919200000001", "rl.p1@example.com", LocalDate.of(1990, 1, 1), Gender.MALE);
        patient1.setUser(u1);
        patient1 = patientRepository.save(patient1);

        patient2 = new Patient("RL Two", "+919200000002", "rl.p2@example.com", LocalDate.of(1991, 2, 2), Gender.FEMALE);
        patient2.setUser(u2);
        patient2 = patientRepository.save(patient2);

        token1 = "Bearer " + tokenProvider.generateAccessToken(u1);
        token2 = "Bearer " + tokenProvider.generateAccessToken(u2);

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
        rateLimiterService.clear();
    }

    /** Valid-shaped body that fails business validation (03:00 is outside the schedule) but consumes the limiter. */
    private String body(Patient p) {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2);
        Instant start = LocalDateTime.of(date, LocalTime.of(3, 0)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        return String.format(
                "{\"patientId\":%d,\"doctorId\":%d,\"branchId\":%d,\"startTime\":\"%s\",\"endTime\":\"%s\",\"type\":\"IN_PERSON\"}",
                p.getId(), doctor.getId(), branch.getId(), start, start.plus(30, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("The 21st booking within a minute returns 429 with the standard ErrorResponse shape")
    void twentyFirstBookingReturns429() throws Exception {
        String json = body(patient1);

        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/appointments").header("Authorization", token1)
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().is4xxClientError());
        }

        mockMvc.perform(post("/api/appointments").header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("A different user is unaffected by another user's booking limit")
    void otherUserUnaffected() throws Exception {
        String json = body(patient1);
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/appointments").header("Authorization", token1)
                    .contentType(MediaType.APPLICATION_JSON).content(json));
        }

        mockMvc.perform(post("/api/appointments").header("Authorization", token1)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/appointments").header("Authorization", token2)
                        .contentType(MediaType.APPLICATION_JSON).content(body(patient2)))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.status").value(not(429)));
    }
}
