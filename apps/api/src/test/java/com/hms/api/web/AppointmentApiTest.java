package com.hms.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.*;
import com.hms.api.dto.BookAppointmentRequest;
import com.hms.api.dto.CancelAppointmentRequest;
import com.hms.api.dto.RescheduleAppointmentRequest;
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
class AppointmentApiTest {

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
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private User adminUser;
    private User doctorUser;
    private User patientUser1;
    private User patientUser2;

    private Doctor doctor;
    private Branch branch;
    private Patient patient1;
    private Patient patient2;

    private String adminToken;
    private String doctorToken;
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

        adminUser = userRepository.save(new User("admin.appt@example.com", "+919200000001", "pass", Role.ADMIN, "Admin"));
        doctorUser = userRepository.save(new User("doc.appt@example.com", "+919200000002", "pass", Role.DOCTOR, "Dr. Specialist"));
        patientUser1 = userRepository.save(new User("pat1.appt@example.com", "+919200000003", null, Role.PATIENT, "Patient Alpha"));
        patientUser2 = userRepository.save(new User("pat2.appt@example.com", "+919200000004", null, Role.PATIENT, "Patient Beta"));

        branch = branchRepository.save(new Branch("Apex Hospital", "APEX01", "Ring Rd", "Pune", "+912011112222", "apex@example.com"));
        doctor = doctorRepository.save(new Doctor(doctorUser, "Specialist", "MD", "English", 12, new BigDecimal("700.00")));

        patient1 = new Patient("Patient Alpha", "+919200000003", "pat1.appt@example.com", LocalDate.of(1993, 4, 12), Gender.MALE);
        patient1.setUser(patientUser1);
        patient1 = patientRepository.save(patient1);

        patient2 = new Patient("Patient Beta", "+919200000004", "pat2.appt@example.com", LocalDate.of(1994, 5, 13), Gender.FEMALE);
        patient2.setUser(patientUser2);
        patient2 = patientRepository.save(patient2);

        adminToken = "Bearer " + tokenProvider.generateAccessToken(adminUser);
        doctorToken = "Bearer " + tokenProvider.generateAccessToken(doctorUser);
        patient1Token = "Bearer " + tokenProvider.generateAccessToken(patientUser1);

        // Schedule: All 7 days from 09:00 to 18:00, 30 min slots
        for (DayOfWeek dow : DayOfWeek.values()) {
            DoctorSchedule sched = new DoctorSchedule(
                    doctor, branch, dow,
                    LocalTime.of(9, 0), LocalTime.of(18, 0),
                    30, LocalDate.now().minusDays(10)
            );
            scheduleRepository.save(sched);
        }
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
    }

    private Instant getFutureSlotStart(int daysAhead, int hour, int minute) {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(daysAhead);
        return LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
    }

    @Test
    @DisplayName("Staff created appointment starts as CONFIRMED immediately")
    void staffBookingConfirmed() throws Exception {
        Instant start = getFutureSlotStart(2, 10, 0);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        BookAppointmentRequest req = new BookAppointmentRequest(
                patient1.getId(), doctor.getId(), branch.getId(),
                start, end, AppointmentType.IN_PERSON, "General checkup"
        );

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Patient created appointment starts as BOOKED")
    void patientBookingStartsAsBooked() throws Exception {
        Instant start = getFutureSlotStart(3, 11, 0);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        BookAppointmentRequest req = new BookAppointmentRequest(
                patient1.getId(), doctor.getId(), branch.getId(),
                start, end, AppointmentType.IN_PERSON, "Headache"
        );

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", patient1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.confirmedAt").doesNotExist());
    }

    @Test
    @DisplayName("Patient cannot book for a different patient (403 Forbidden)")
    void patientCannotBookForOthers() throws Exception {
        Instant start = getFutureSlotStart(3, 11, 30);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        // patient1 tries to book for patient2
        BookAppointmentRequest req = new BookAppointmentRequest(
                patient2.getId(), doctor.getId(), branch.getId(),
                start, end, AppointmentType.IN_PERSON, "Checkup"
        );

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", patient1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Booking overlapping active slot returns 409 Conflict")
    void doubleBookingReturns409() throws Exception {
        Instant start = getFutureSlotStart(4, 14, 0);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        BookAppointmentRequest req = new BookAppointmentRequest(
                patient1.getId(), doctor.getId(), branch.getId(),
                start, end, AppointmentType.IN_PERSON, "First booking"
        );

        // First booking succeeds
        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Second booking for identical slot fails with 409 Conflict
        BookAppointmentRequest conflictReq = new BookAppointmentRequest(
                patient2.getId(), doctor.getId(), branch.getId(),
                start, end, AppointmentType.IN_PERSON, "Conflicting booking"
        );

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Walk-in by staff with reason succeeds outside regular template")
    void staffWalkInSucceeds() throws Exception {
        Instant start = getFutureSlotStart(1, 19, 15); // Outside 09:00 - 18:00 template
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        BookAppointmentRequest req = new BookAppointmentRequest(
                patient1.getId(), doctor.getId(), branch.getId(),
                start, end, AppointmentType.WALK_IN, "Urgent evening walk-in"
        );

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WALK_IN"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("Lifecycle: confirm, check-in, start-consultation, complete")
    void appointmentLifecycleFlow() throws Exception {
        // Today's appointment for check-in test
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        Instant start = LocalDateTime.of(today, LocalTime.of(15, 0)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        // 1. Patient books appointment -> BOOKED
        BookAppointmentRequest bookReq = new BookAppointmentRequest(
                patient1.getId(), doctor.getId(), branch.getId(),
                start, end, AppointmentType.IN_PERSON, "Flu symptoms"
        );

        String bookResponse = mockMvc.perform(post("/api/appointments")
                        .header("Authorization", patient1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andReturn().getResponse().getContentAsString();

        Long apptId = objectMapper.readTree(bookResponse).get("id").asLong();

        // 2. Staff confirms -> CONFIRMED
        mockMvc.perform(patch("/api/appointments/" + apptId + "/confirm")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // 3. Staff checks in (today) -> CHECKED_IN
        mockMvc.perform(patch("/api/appointments/" + apptId + "/check-in")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));

        // 4. Doctor starts consultation -> IN_CONSULTATION
        mockMvc.perform(patch("/api/appointments/" + apptId + "/start-consultation")
                        .header("Authorization", doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_CONSULTATION"));

        // 5. Doctor completes consultation -> COMPLETED
        mockMvc.perform(patch("/api/appointments/" + apptId + "/complete")
                        .header("Authorization", doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Cancelling appointment frees the slot for immediate rebooking")
    void cancelFreesSlotForRebooking() throws Exception {
        Instant start = getFutureSlotStart(5, 10, 0);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        BookAppointmentRequest bookReq = new BookAppointmentRequest(
                patient1.getId(), doctor.getId(), branch.getId(),
                start, end, AppointmentType.IN_PERSON, "Dental pain"
        );

        String bookResponse = mockMvc.perform(post("/api/appointments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long apptId = objectMapper.readTree(bookResponse).get("id").asLong();

        // Cancel
        CancelAppointmentRequest cancelReq = new CancelAppointmentRequest("Patient feeling better");
        mockMvc.perform(patch("/api/appointments/" + apptId + "/cancel")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Immediate rebooking of the exact same slot by patient2 succeeds!
        BookAppointmentRequest rebookReq = new BookAppointmentRequest(
                patient2.getId(), doctor.getId(), branch.getId(),
                start, end, AppointmentType.IN_PERSON, "New booking in freed slot"
        );

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rebookReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("Reschedule moves appointment, marks old as RESCHEDULED, and frees old slot")
    void rescheduleFlow() throws Exception {
        Instant oldStart = getFutureSlotStart(6, 11, 0);
        Instant oldEnd = oldStart.plus(30, ChronoUnit.MINUTES);

        BookAppointmentRequest bookReq = new BookAppointmentRequest(
                patient1.getId(), doctor.getId(), branch.getId(),
                oldStart, oldEnd, AppointmentType.IN_PERSON, "Knee pain"
        );

        String bookResponse = mockMvc.perform(post("/api/appointments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long oldApptId = objectMapper.readTree(bookResponse).get("id").asLong();

        // Reschedule to a new slot
        Instant newStart = getFutureSlotStart(7, 11, 0);
        Instant newEnd = newStart.plus(30, ChronoUnit.MINUTES);
        RescheduleAppointmentRequest reschedReq = new RescheduleAppointmentRequest(
                newStart, newEnd, branch.getId(), "Doctor rescheduled"
        );

        mockMvc.perform(post("/api/appointments/" + oldApptId + "/reschedule")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reschedReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.rescheduledFromId").value(oldApptId));

        // The old slot (oldStart) is now freed! Another patient can book it!
        BookAppointmentRequest bookFreedSlotReq = new BookAppointmentRequest(
                patient2.getId(), doctor.getId(), branch.getId(),
                oldStart, oldEnd, AppointmentType.IN_PERSON, "Booking vacated slot"
        );

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookFreedSlotReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }
}
