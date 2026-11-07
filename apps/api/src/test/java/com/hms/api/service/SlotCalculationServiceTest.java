package com.hms.api.service;

import com.hms.api.domain.*;
import com.hms.api.dto.AvailableSlotDto;
import com.hms.api.repository.AppointmentRepository;
import com.hms.api.repository.DoctorScheduleRepository;
import com.hms.api.repository.ScheduleExceptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotCalculationServiceTest {

    @Mock
    private DoctorScheduleRepository scheduleRepository;

    @Mock
    private ScheduleExceptionRepository exceptionRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    private SlotCalculationService slotService;
    private Clock fixedClock;
    private ZoneId zone;
    private Doctor doctor;
    private Branch branch;

    @BeforeEach
    void setUp() {
        // Monday, 2026-11-02 08:00:00 UTC
        fixedClock = Clock.fixed(Instant.parse("2026-11-02T08:00:00Z"), ZoneOffset.UTC);
        zone = ZoneOffset.UTC;

        slotService = new SlotCalculationService(
                scheduleRepository,
                exceptionRepository,
                appointmentRepository,
                fixedClock,
                "UTC",
                60,
                30
        );

        User user = new User("dr@example.com", "+919000000001", "pass", Role.DOCTOR, "Dr. Test");
        doctor = new Doctor(user, "Bio", "MBBS", "English", 10, new BigDecimal("500.00"));
        doctor.setId(1L);

        branch = new Branch("Main", "MAIN", "Street", "City", "+9111111111", "m@example.com");
        branch.setId(10L);
    }

    @Test
    @DisplayName("Should generate slots within schedule and exclude break windows")
    void generateSlotsExcludingBreak() {
        LocalDate date = LocalDate.of(2026, 11, 2); // Monday

        // Schedule: 09:00 to 12:00, 30 min slots, break 10:30 to 11:00
        DoctorSchedule schedule = new DoctorSchedule(
                doctor, branch, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0),
                30, LocalDate.of(2026, 1, 1)
        );
        schedule.setBreakStart(LocalTime.of(10, 30));
        schedule.setBreakEnd(LocalTime.of(11, 0));

        when(scheduleRepository.findByDoctorIdAndIsActiveTrue(1L)).thenReturn(List.of(schedule));
        when(exceptionRepository.findByDoctorIdAndDateBetween(eq(1L), any(), any())).thenReturn(Collections.emptyList());
        when(appointmentRepository.findActiveByDoctorInRange(eq(1L), any(), any())).thenReturn(Collections.emptyList());

        List<AvailableSlotDto> slots = slotService.calculateAvailableSlots(1L, date, date, 10L);

        // Expected slots: 09:00-09:30, 09:30-10:00, 10:00-10:30, (break), 11:00-11:30, 11:30-12:00 -> total 5 slots
        assertThat(slots).hasSize(5);

        List<LocalTime> startTimes = slots.stream()
                .map(s -> LocalDateTime.ofInstant(s.startTime(), zone).toLocalTime())
                .toList();

        assertThat(startTimes).containsExactly(
                LocalTime.of(9, 0),
                LocalTime.of(9, 30),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                LocalTime.of(11, 30)
        );
    }

    @Test
    @DisplayName("Should exclude slots that overlap doctor leave exception")
    void excludeLeaveSlots() {
        LocalDate date = LocalDate.of(2026, 11, 2);

        DoctorSchedule schedule = new DoctorSchedule(
                doctor, branch, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0),
                30, LocalDate.of(2026, 1, 1)
        );

        ScheduleException leave = new ScheduleException(doctor, date, ScheduleExceptionType.LEAVE, "Conference");
        // Full day leave
        leave.setStartTime(null);
        leave.setEndTime(null);

        when(scheduleRepository.findByDoctorIdAndIsActiveTrue(1L)).thenReturn(List.of(schedule));
        when(exceptionRepository.findByDoctorIdAndDateBetween(eq(1L), any(), any())).thenReturn(List.of(leave));
        when(appointmentRepository.findActiveByDoctorInRange(eq(1L), any(), any())).thenReturn(Collections.emptyList());

        List<AvailableSlotDto> slots = slotService.calculateAvailableSlots(1L, date, date, null);
        assertThat(slots).isEmpty();
    }

    @Test
    @DisplayName("Should exclude slots overlapping existing active appointments")
    void excludeActiveAppointments() {
        LocalDate date = LocalDate.of(2026, 11, 2);

        DoctorSchedule schedule = new DoctorSchedule(
                doctor, branch, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(11, 0),
                30, LocalDate.of(2026, 1, 1)
        );

        // Active appointment at 09:30 - 10:00
        Instant apptStart = LocalDateTime.of(date, LocalTime.of(9, 30)).atZone(zone).toInstant();
        Instant apptEnd = apptStart.plus(30, ChronoUnit.MINUTES);
        Appointment activeAppt = new Appointment();
        activeAppt.setStartTime(apptStart);
        activeAppt.setEndTime(apptEnd);
        activeAppt.setStatus(AppointmentStatus.CONFIRMED);

        when(scheduleRepository.findByDoctorIdAndIsActiveTrue(1L)).thenReturn(List.of(schedule));
        when(exceptionRepository.findByDoctorIdAndDateBetween(eq(1L), any(), any())).thenReturn(Collections.emptyList());
        when(appointmentRepository.findActiveByDoctorInRange(eq(1L), any(), any())).thenReturn(List.of(activeAppt));

        List<AvailableSlotDto> slots = slotService.calculateAvailableSlots(1L, date, date, null);

        // Slots: 09:00, (09:30 taken), 10:00, 10:30 -> total 3
        assertThat(slots).hasSize(3);
        List<LocalTime> startTimes = slots.stream()
                .map(s -> LocalDateTime.ofInstant(s.startTime(), zone).toLocalTime())
                .toList();

        assertThat(startTimes).doesNotContain(LocalTime.of(9, 30));
    }

    @Test
    @DisplayName("Should exclude slots that violate minimum lead time (30 mins from now)")
    void enforceMinLeadTime() {
        // Now is 08:00. Min lead is 30 mins -> slot must start >= 08:30
        LocalDate date = LocalDate.of(2026, 11, 2);

        DoctorSchedule schedule = new DoctorSchedule(
                doctor, branch, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(10, 0),
                15, LocalDate.of(2026, 1, 1)
        );

        when(scheduleRepository.findByDoctorIdAndIsActiveTrue(1L)).thenReturn(List.of(schedule));
        when(exceptionRepository.findByDoctorIdAndDateBetween(eq(1L), any(), any())).thenReturn(Collections.emptyList());
        when(appointmentRepository.findActiveByDoctorInRange(eq(1L), any(), any())).thenReturn(Collections.emptyList());

        List<AvailableSlotDto> slots = slotService.calculateAvailableSlots(1L, date, date, null);

        // 08:00 and 08:15 should be excluded because 08:00 + 30m = 08:30
        List<LocalTime> startTimes = slots.stream()
                .map(s -> LocalDateTime.ofInstant(s.startTime(), zone).toLocalTime())
                .toList();

        assertThat(startTimes).doesNotContain(LocalTime.of(8, 0), LocalTime.of(8, 15));
        assertThat(startTimes).contains(LocalTime.of(8, 30), LocalTime.of(8, 45));
    }
}

