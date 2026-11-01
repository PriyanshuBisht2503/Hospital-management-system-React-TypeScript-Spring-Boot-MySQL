package com.hms.api.repository;

import com.hms.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class DoctorScheduleRepositoryTest {

    @Autowired
    private DoctorScheduleRepository scheduleRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    private Doctor doctor;
    private Branch branch;

    @BeforeEach
    void setUp() {
        User user = new User("dr.schedule@example.com", "+919555500001", "password", Role.DOCTOR, "Dr. Schedule Test");
        userRepository.save(user);

        doctor = new Doctor(user, "Bio", "MBBS", "English, Hindi", 10, new BigDecimal("500.00"));
        doctorRepository.save(doctor);

        branch = new Branch("Main Hospital", "MAIN01", "123 Health Ave", "Mumbai", "+912211112222", "main@example.com");
        branchRepository.save(branch);
    }

    @Test
    @DisplayName("Should persist valid doctor schedule template")
    void shouldPersistDoctorSchedule() {
        DoctorSchedule schedule = new DoctorSchedule(
                doctor, branch, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0),
                30, LocalDate.now()
        );
        schedule.setBreakStart(LocalTime.of(13, 0));
        schedule.setBreakEnd(LocalTime.of(14, 0));

        DoctorSchedule saved = scheduleRepository.save(schedule);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDoctor().getId()).isEqualTo(doctor.getId());
        assertThat(saved.getSlotMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("Should detect overlapping schedule on same day of week for doctor")
    void shouldDetectOverlappingSchedule() {
        DoctorSchedule s1 = new DoctorSchedule(
                doctor, branch, DayOfWeek.TUESDAY,
                LocalTime.of(9, 0), LocalTime.of(13, 0),
                30, LocalDate.now()
        );
        scheduleRepository.save(s1);

        // Overlapping: 11:00 to 15:00
        List<DoctorSchedule> overlaps = scheduleRepository.findOverlappingSchedules(
                doctor.getId(), DayOfWeek.TUESDAY,
                LocalTime.of(11, 0), LocalTime.of(15, 0), null
        );
        assertThat(overlaps).hasSize(1);

        // Non-overlapping: 14:00 to 18:00
        List<DoctorSchedule> nonOverlaps = scheduleRepository.findOverlappingSchedules(
                doctor.getId(), DayOfWeek.TUESDAY,
                LocalTime.of(14, 0), LocalTime.of(18, 0), null
        );
        assertThat(nonOverlaps).isEmpty();
    }

    @Test
    @DisplayName("Should reject invalid schedule where break is outside working hours")
    void shouldRejectBreakOutsideWorkingHours() {
        DoctorSchedule schedule = new DoctorSchedule(
                doctor, branch, DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(13, 0),
                30, LocalDate.now()
        );
        schedule.setBreakStart(LocalTime.of(14, 0));
        schedule.setBreakEnd(LocalTime.of(15, 0));

        assertThatThrownBy(() -> scheduleRepository.saveAndFlush(schedule))
                .isInstanceOf(Exception.class);
    }
}
