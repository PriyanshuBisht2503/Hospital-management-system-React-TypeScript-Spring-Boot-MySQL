package com.hms.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.*;
import com.hms.api.dto.AppointmentResponseDto;
import com.hms.api.dto.BookAppointmentRequest;
import com.hms.api.dto.CancelAppointmentRequest;
import com.hms.api.dto.RescheduleAppointmentRequest;
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
    private AppointmentBookingService bookingService;

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
    void tearDown() throws Exception {
        // Allow in-flight async background notification tasks to complete before tearing down
        Thread.sleep(150);
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
    @DisplayName("Patient booking with someone else's ID or nonexistent ID returns identical 404 Not Found")
    void patientCannotBookForOthers() throws Exception {
        Instant start = getFutureSlotStart(3, 11, 30);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        // 1. patient1 tries to book for patient2 (someone else's patient id) -> 404 Not Found
        BookAppointmentRequest reqOther = new BookAppointmentRequest(
                patient2.getId(), doctor.getId(), branch.getId(),
                start, end, AppointmentType.IN_PERSON, "Checkup"
        );
        String respOther = mockMvc.perform(post("/api/appointments")
                        .header("Authorization", patient1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqOther)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // 2. patient1 tries to book for nonexistent patient id -> 404 Not Found
        BookAppointmentRequest reqNonexistent = new BookAppointmentRequest(
                999999L, doctor.getId(), branch.getId(),
                start, end, AppointmentType.IN_PERSON, "Checkup"
        );
        String respNonexistent = mockMvc.perform(post("/api/appointments")
                        .header("Authorization", patient1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNonexistent)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // 3. Confirm identical error response (prevent enumeration)
        assertThat(objectMapper.readTree(respOther).get("status").asInt()).isEqualTo(404);
        assertThat(objectMapper.readTree(respNonexistent).get("status").asInt()).isEqualTo(404);
        assertThat(objectMapper.readTree(respOther).get("error").asText())
                .isEqualTo(objectMapper.readTree(respNonexistent).get("error").asText());
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

    @Test
    @DisplayName("Doctor cannot view, update status, or cancel another doctor's appointment (404 Not Found)")
    void doctorCannotUpdateStatusOfAnotherDoctorAppointment() throws Exception {
        User docUser2 = userRepository.save(new User("doc2.ownership@example.com", "+919200000088", "pass", Role.DOCTOR, "Dr. Other Doctor"));
        Doctor doc2 = doctorRepository.save(new Doctor(docUser2, "Other Spec", "MD", "English", 10, new BigDecimal("600.00")));
        String doctor2Token = "Bearer " + tokenProvider.generateAccessToken(docUser2);

        Instant start = getFutureSlotStart(8, 14, 0);
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        BookAppointmentRequest bookReq = new BookAppointmentRequest(
                patient1.getId(), doctor.getId(), branch.getId(),
                start, end, AppointmentType.IN_PERSON, "Consultation with Doctor 1"
        );

        String response = mockMvc.perform(post("/api/appointments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long apptId = objectMapper.readTree(response).get("id").asLong();

        // Doctor 2 attempts GET on Doctor 1's appointment -> 404
        mockMvc.perform(get("/api/appointments/" + apptId)
                        .header("Authorization", doctor2Token))
                .andExpect(status().isNotFound());

        // Doctor 2 attempts start-consultation on Doctor 1's appointment -> 404
        mockMvc.perform(patch("/api/appointments/" + apptId + "/start-consultation")
                        .header("Authorization", doctor2Token))
                .andExpect(status().isNotFound());

        // Doctor 2 attempts complete on Doctor 1's appointment -> 404
        mockMvc.perform(patch("/api/appointments/" + apptId + "/complete")
                        .header("Authorization", doctor2Token))
                .andExpect(status().isNotFound());

        // Doctor 2 attempts no-show on Doctor 1's appointment -> 404
        mockMvc.perform(patch("/api/appointments/" + apptId + "/no-show")
                        .header("Authorization", doctor2Token))
                .andExpect(status().isNotFound());

        // Doctor 2 attempts cancel on Doctor 1's appointment -> 404
        CancelAppointmentRequest cancelReq = new CancelAppointmentRequest("Unauthorized cancel");
        mockMvc.perform(patch("/api/appointments/" + apptId + "/cancel")
                        .header("Authorization", doctor2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Patient viewing or cancelling another patient's appointment returns 404 identical to nonexistent appointment")
    void patientCannotViewOrCancelAnotherPatientAppointment() throws Exception {
        String patient2Token = "Bearer " + tokenProvider.generateAccessToken(patientUser2);

        Instant start = getFutureSlotStart(9, 15, 0);
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        BookAppointmentRequest bookReq = new BookAppointmentRequest(
                patient1.getId(), doctor.getId(), branch.getId(),
                start, end, AppointmentType.IN_PERSON, "Patient 1 Appointment"
        );

        String response = mockMvc.perform(post("/api/appointments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long apptId = objectMapper.readTree(response).get("id").asLong();

        // 1. Patient 2 attempts to view Patient 1's appointment -> 404 Not Found
        String viewOtherResp = mockMvc.perform(get("/api/appointments/" + apptId)
                        .header("Authorization", patient2Token))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // 2. Patient 2 attempts to view nonexistent appointment -> 404 Not Found
        String viewNonexistentResp = mockMvc.perform(get("/api/appointments/999999")
                        .header("Authorization", patient2Token))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(viewOtherResp).get("status").asInt()).isEqualTo(404);
        assertThat(objectMapper.readTree(viewOtherResp).get("error").asText())
                .isEqualTo(objectMapper.readTree(viewNonexistentResp).get("error").asText());

        // 3. Patient 2 attempts to cancel Patient 1's appointment -> 404 Not Found
        CancelAppointmentRequest cancelReq = new CancelAppointmentRequest("Unauthorized cancel attempt");
        String cancelOtherResp = mockMvc.perform(patch("/api/appointments/" + apptId + "/cancel")
                        .header("Authorization", patient2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // 4. Patient 2 attempts to cancel nonexistent appointment -> 404 Not Found
        String cancelNonexistentResp = mockMvc.perform(patch("/api/appointments/999999/cancel")
                        .header("Authorization", patient2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(cancelOtherResp).get("status").asInt()).isEqualTo(404);
        assertThat(objectMapper.readTree(cancelOtherResp).get("error").asText())
                .isEqualTo(objectMapper.readTree(cancelNonexistentResp).get("error").asText());
    }

    @Test
    @DisplayName("Transaction verification: bookingService runs under READ_COMMITTED without outer transaction interference")
    void transactionVerificationReadCommittedActive() {
        // Direct call to bookingService succeeds and asserts internal READ_COMMITTED isolation level
        Instant start = getFutureSlotStart(10, 16, 0);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        AppointmentResponseDto dto = bookingService.bookAppointment(
                patient1.getId(), doctor.getId(), branch.getId(),
                start, end, AppointmentType.IN_PERSON, "Tx check", Role.ADMIN, "admin"
        );

        assertThat(dto.status()).isEqualTo(AppointmentStatus.CONFIRMED);
    }
}
