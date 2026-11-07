package com.hms.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.*;
import com.hms.api.repository.*;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DoctorSearchAndSlotApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleExceptionRepository exceptionRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

    private Doctor doctorWithSchedule;
    private Doctor doctorWithoutSchedule;
    private Branch branch;
    private Specialty cardio;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        scheduleRepository.deleteAll();
        exceptionRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        specialtyRepository.deleteAll();
        userRepository.deleteAll();

        branch = branchRepository.save(new Branch("Central Branch", "CENTRAL01", "Central Rd", "Metro", "+91900888111", "central@example.com"));
        cardio = specialtyRepository.save(new Specialty("Cardiology", "CARDIO", "Heart health"));

        User docUser1 = userRepository.save(new User("cardio.doc@example.com", "+919500000001", "pass", Role.DOCTOR, "Dr. Cardio Heart"));
        doctorWithSchedule = new Doctor(docUser1, "Heart Specialist", "MD Cardiology", "English, Hindi", 15, new BigDecimal("800.00"));
        doctorWithSchedule.setSpecialties(Set.of(cardio));
        doctorWithSchedule.setBranches(Set.of(branch));
        doctorWithSchedule = doctorRepository.save(doctorWithSchedule);

        User docUser2 = userRepository.save(new User("nosched.doc@example.com", "+919500000002", "pass", Role.DOCTOR, "Dr. No Sched"));
        doctorWithoutSchedule = new Doctor(docUser2, "General", "MBBS", "English", 2, new BigDecimal("300.00"));
        doctorWithoutSchedule.setBranches(Set.of(branch));
        doctorWithoutSchedule = doctorRepository.save(doctorWithoutSchedule);

        // Add 7-day schedule for doctorWithSchedule
        for (DayOfWeek dow : DayOfWeek.values()) {
            DoctorSchedule sched = new DoctorSchedule(
                    doctorWithSchedule, branch, dow,
                    LocalTime.of(9, 0), LocalTime.of(17, 0),
                    30, LocalDate.now().minusDays(5)
            );
            scheduleRepository.save(sched);
        }
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        scheduleRepository.deleteAll();
        exceptionRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        specialtyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Public doctor search returns doctors with nextAvailableSlot computed anonymously")
    void publicDoctorSearchComputesNextAvailableSlot() throws Exception {
        mockMvc.perform(get("/api/doctors/search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[?(@.id == " + doctorWithSchedule.getId() + ")].nextAvailableSlot", notNullValue()))
                .andExpect(jsonPath("$.content[?(@.id == " + doctorWithoutSchedule.getId() + ")].nextAvailableSlot", contains(nullValue())));
    }

    @Test
    @DisplayName("Public doctor search filters by specialty and branch")
    void publicDoctorSearchFilters() throws Exception {
        mockMvc.perform(get("/api/doctors/search")
                        .param("specialtyId", cardio.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(doctorWithSchedule.getId()));
    }

    @Test
    @DisplayName("Public slots endpoint returns available slots for doctor anonymously")
    void publicDoctorSlotsEndpoint() throws Exception {
        LocalDate queryDate = LocalDate.now().plusDays(2);
        mockMvc.perform(get("/api/doctors/" + doctorWithSchedule.getId() + "/slots")
                        .param("date", queryDate.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].doctorId").value(doctorWithSchedule.getId()))
                .andExpect(jsonPath("$[0].slotMinutes").value(30));
    }
}
