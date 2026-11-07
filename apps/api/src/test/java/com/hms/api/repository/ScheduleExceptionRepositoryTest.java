package com.hms.api.repository;

import com.hms.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ScheduleExceptionRepositoryTest {

    @Autowired
    private ScheduleExceptionRepository exceptionRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private UserRepository userRepository;

    private Doctor doctor;

    @BeforeEach
    void setUp() {
        User user = new User("dr.exception@example.com", "+919555500002", "password", Role.DOCTOR, "Dr. Exception Test");
        userRepository.save(user);

        doctor = new Doctor(user, "Bio", "MBBS", "English", 12, new BigDecimal("600.00"));
        doctorRepository.save(doctor);
    }

    @Test
    @DisplayName("Should persist leave exception and query by date")
    void shouldPersistAndQueryLeave() {
        LocalDate leaveDate = LocalDate.now().plusDays(5);
        ScheduleException leave = new ScheduleException(doctor, leaveDate, ScheduleExceptionType.LEAVE, "Family function");
        exceptionRepository.save(leave);

        List<ScheduleException> leaves = exceptionRepository.findLeavesOnDate(doctor.getId(), leaveDate);
        assertThat(leaves).hasSize(1);
        assertThat(leaves.get(0).getType()).isEqualTo(ScheduleExceptionType.LEAVE);
        assertThat(leaves.get(0).getReason()).isEqualTo("Family function");
    }

    @Test
    @DisplayName("Should persist extra hours exception with start and end times")
    void shouldPersistExtraHours() {
        LocalDate date = LocalDate.now().plusDays(2);
        ScheduleException extra = new ScheduleException(doctor, date, ScheduleExceptionType.EXTRA_HOURS, "Evening clinic");
        extra.setStartTime(LocalTime.of(18, 0));
        extra.setEndTime(LocalTime.of(20, 0));
        exceptionRepository.save(extra);

        List<ScheduleException> found = exceptionRepository.findByDoctorIdAndDate(doctor.getId(), date);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(found.get(0).getEndTime()).isEqualTo(LocalTime.of(20, 0));
    }
}
