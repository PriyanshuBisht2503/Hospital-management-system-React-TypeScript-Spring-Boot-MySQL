package com.hms.api.service;

import com.hms.api.domain.*;
import com.hms.api.dto.AppointmentResponseDto;
import com.hms.api.exception.BadRequestException;
import com.hms.api.exception.ConflictException;
import com.hms.api.repository.AppointmentRepository;
import com.hms.api.repository.BranchRepository;
import com.hms.api.repository.DoctorRepository;
import com.hms.api.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentBookingServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private SlotCalculationService slotCalculationService;

    private AppointmentStateMachine stateMachine;
    private AppointmentBookingService bookingService;
    private Clock fixedClock;

    private Doctor doctor;
    private Patient patient;
    private Branch branch;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);
        stateMachine = new AppointmentStateMachine();

        bookingService = new AppointmentBookingService(
                appointmentRepository,
                doctorRepository,
                patientRepository,
                branchRepository,
                slotCalculationService,
                stateMachine,
                fixedClock,
                "UTC"
        );

        User docUser = new User("dr@example.com", "+919000000001", "pass", Role.DOCTOR, "Dr. Test");
        doctor = new Doctor(docUser, "Bio", "MBBS", "English", 10, new BigDecimal("500.00"));
        doctor.setId(1L);

        patient = new Patient("Patient A", "+919000000002", "pat@example.com", null, Gender.MALE);
        patient.setId(2L);

        branch = new Branch("Main Branch", "MAIN", "Street", "City", "+9111111111", "m@example.com");
        branch.setId(3L);

        lenient().when(doctorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(doctor));
        lenient().when(patientRepository.findById(2L)).thenReturn(Optional.of(patient));
        lenient().when(branchRepository.findById(3L)).thenReturn(Optional.of(branch));
    }

    @Test
    @DisplayName("Staff created appointment starts as CONFIRMED immediately")
    void staffBookingConfirmedImmediately() {
        Instant start = fixedClock.instant().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        when(slotCalculationService.isSlotOpen(1L, start, end, 3L)).thenReturn(true);
        when(appointmentRepository.findDoctorOverlappingAppointments(1L, start, end, null)).thenReturn(Collections.emptyList());
        when(appointmentRepository.findPatientOverlappingAppointments(2L, start, end, null)).thenReturn(Collections.emptyList());
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentResponseDto result = bookingService.bookAppointment(
                2L, 1L, 3L, start, end, AppointmentType.IN_PERSON, "Regular checkup", "ADMIN", "admin123"
        );

        assertThat(result.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(result.confirmedAt()).isEqualTo(fixedClock.instant());
        assertThat(result.createdBy()).isEqualTo("admin123");
    }

    @Test
    @DisplayName("Patient created appointment starts as BOOKED")
    void patientBookingStartsAsBooked() {
        Instant start = fixedClock.instant().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        when(slotCalculationService.isSlotOpen(1L, start, end, 3L)).thenReturn(true);
        when(appointmentRepository.findDoctorOverlappingAppointments(1L, start, end, null)).thenReturn(Collections.emptyList());
        when(appointmentRepository.findPatientOverlappingAppointments(2L, start, end, null)).thenReturn(Collections.emptyList());
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentResponseDto result = bookingService.bookAppointment(
                2L, 1L, 3L, start, end, AppointmentType.IN_PERSON, "Fever", "PATIENT", "patient123"
        );

        assertThat(result.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(result.confirmedAt()).isNull();
    }

    @Test
    @DisplayName("Walk-in by staff with reason is allowed outside template slots")
    void walkInByStaffAllowedOutsideTemplate() {
        Instant start = fixedClock.instant().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        when(appointmentRepository.findDoctorOverlappingAppointments(1L, start, end, null)).thenReturn(Collections.emptyList());
        when(appointmentRepository.findPatientOverlappingAppointments(2L, start, end, null)).thenReturn(Collections.emptyList());
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentResponseDto result = bookingService.bookAppointment(
                2L, 1L, 3L, start, end, AppointmentType.WALK_IN, "Emergency walk-in", "RECEPTIONIST", "receptionist1"
        );

        assertThat(result.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(result.type()).isEqualTo(AppointmentType.WALK_IN);
    }

    @Test
    @DisplayName("Walk-in by patient or without reason is rejected")
    void walkInValidation() {
        Instant start = fixedClock.instant().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        // Patient attempting walk-in
        assertThatThrownBy(() -> bookingService.bookAppointment(
                2L, 1L, 3L, start, end, AppointmentType.WALK_IN, "Emergency", "PATIENT", "pat1"
        )).isInstanceOf(BadRequestException.class);

        // Staff walk-in without reason
        assertThatThrownBy(() -> bookingService.bookAppointment(
                2L, 1L, 3L, start, end, AppointmentType.WALK_IN, "", "ADMIN", "admin1"
        )).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Booking rejected if patient already has overlapping active appointment")
    void patientOverlapRejected() {
        Instant start = fixedClock.instant().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        when(slotCalculationService.isSlotOpen(1L, start, end, 3L)).thenReturn(true);
        when(appointmentRepository.findDoctorOverlappingAppointments(1L, start, end, null)).thenReturn(Collections.emptyList());
        when(appointmentRepository.findPatientOverlappingAppointments(2L, start, end, null)).thenReturn(List.of(new Appointment()));

        assertThatThrownBy(() -> bookingService.bookAppointment(
                2L, 1L, 3L, start, end, AppointmentType.IN_PERSON, "Checkup", "PATIENT", "pat1"
        )).isInstanceOf(ConflictException.class)
          .hasMessageContaining("Patient already has an active appointment");
    }

    @Test
    @DisplayName("Reschedule marks old appointment as RESCHEDULED and creates new CONFIRMED appointment")
    void rescheduleAppointmentFlow() {
        Instant oldStart = fixedClock.instant().plus(1, ChronoUnit.DAYS);
        Instant oldEnd = oldStart.plus(30, ChronoUnit.MINUTES);

        Appointment oldAppt = new Appointment(patient, doctor, branch, oldStart, oldEnd, AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        oldAppt.setId(100L);
        oldAppt.setNeedsRescheduling(true);

        Instant newStart = fixedClock.instant().plus(3, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(30, ChronoUnit.MINUTES);

        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(oldAppt));
        when(slotCalculationService.isSlotOpen(1L, newStart, newEnd, 3L)).thenReturn(true);
        when(appointmentRepository.findDoctorOverlappingAppointments(1L, newStart, newEnd, 100L)).thenReturn(Collections.emptyList());
        when(appointmentRepository.findPatientOverlappingAppointments(2L, newStart, newEnd, 100L)).thenReturn(Collections.emptyList());
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentResponseDto rescheduled = bookingService.rescheduleAppointment(
                100L, newStart, newEnd, 3L, "Doctor requested reschedule", "ADMIN", "admin1"
        );

        // Verify old appointment state
        assertThat(oldAppt.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
        assertThat(oldAppt.isNeedsRescheduling()).isFalse();

        // Verify new appointment state
        assertThat(rescheduled.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(rescheduled.rescheduledFromId()).isEqualTo(100L);
        assertThat(rescheduled.startTime()).isEqualTo(newStart);
    }
}
