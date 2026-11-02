package com.hms.api.service;

import com.hms.api.domain.*;
import com.hms.api.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentStateMachineTest {

    private AppointmentStateMachine stateMachine;
    private Clock fixedClock;
    private ZoneId zone;

    @BeforeEach
    void setUp() {
        stateMachine = new AppointmentStateMachine();
        // Fixed clock at 2026-11-02 10:00:00 UTC
        fixedClock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);
        zone = ZoneOffset.UTC;
    }

    private Appointment createAppointment(AppointmentStatus status, Instant start, Instant end) {
        Appointment a = new Appointment();
        a.setStatus(status);
        a.setStartTime(start);
        a.setEndTime(end);
        return a;
    }

    @Test
    @DisplayName("Valid transition BOOKED -> CONFIRMED sets confirmed_at")
    void bookedToConfirmed() {
        Appointment a = createAppointment(AppointmentStatus.BOOKED, fixedClock.instant().plus(1, ChronoUnit.DAYS), fixedClock.instant().plus(1, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES));
        stateMachine.applyTransition(a, AppointmentStatus.CONFIRMED, fixedClock, zone);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(a.getConfirmedAt()).isEqualTo(fixedClock.instant());
    }

    @Test
    @DisplayName("Valid transition BOOKED -> RESCHEDULED sets status to RESCHEDULED")
    void bookedToRescheduled() {
        Appointment a = createAppointment(AppointmentStatus.BOOKED, fixedClock.instant().plus(2, ChronoUnit.DAYS), fixedClock.instant().plus(2, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES));
        stateMachine.applyTransition(a, AppointmentStatus.RESCHEDULED, fixedClock, zone);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
    }

    @Test
    @DisplayName("Invalid transition BOOKED -> CHECKED_IN throws ConflictException")
    void invalidDirectTransitionThrows() {
        Appointment a = createAppointment(AppointmentStatus.BOOKED, fixedClock.instant(), fixedClock.instant().plus(30, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> stateMachine.applyTransition(a, AppointmentStatus.CHECKED_IN, fixedClock, zone))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Invalid appointment status transition");
    }

    @Test
    @DisplayName("Check-in permitted only on the day of the appointment")
    void checkInOnlyOnAppointmentDay() {
        // Appointment is tomorrow
        Appointment future = createAppointment(AppointmentStatus.CONFIRMED, fixedClock.instant().plus(1, ChronoUnit.DAYS), fixedClock.instant().plus(1, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES));
        assertThatThrownBy(() -> stateMachine.applyTransition(future, AppointmentStatus.CHECKED_IN, fixedClock, zone))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Check-in is permitted only on the day of the appointment");

        // Appointment is today
        Appointment today = createAppointment(AppointmentStatus.CONFIRMED, fixedClock.instant().plus(2, ChronoUnit.HOURS), fixedClock.instant().plus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES));
        stateMachine.applyTransition(today, AppointmentStatus.CHECKED_IN, fixedClock, zone);
        assertThat(today.getStatus()).isEqualTo(AppointmentStatus.CHECKED_IN);
        assertThat(today.getCheckedInAt()).isEqualTo(fixedClock.instant());
    }

    @Test
    @DisplayName("NO_SHOW only after appointment start time")
    void noShowOnlyAfterStartTime() {
        // Start time is in 1 hour
        Appointment future = createAppointment(AppointmentStatus.CONFIRMED, fixedClock.instant().plus(1, ChronoUnit.HOURS), fixedClock.instant().plus(2, ChronoUnit.HOURS));
        assertThatThrownBy(() -> stateMachine.applyTransition(future, AppointmentStatus.NO_SHOW, fixedClock, zone))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot mark as NO_SHOW before appointment start time");

        // Start time was 10 minutes ago
        Appointment past = createAppointment(AppointmentStatus.CONFIRMED, fixedClock.instant().minus(10, ChronoUnit.MINUTES), fixedClock.instant().plus(20, ChronoUnit.MINUTES));
        stateMachine.applyTransition(past, AppointmentStatus.NO_SHOW, fixedClock, zone);
        assertThat(past.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @Test
    @DisplayName("COMPLETE only from IN_CONSULTATION state")
    void completeOnlyFromInConsultation() {
        Appointment confirmed = createAppointment(AppointmentStatus.CONFIRMED, fixedClock.instant().minus(30, ChronoUnit.MINUTES), fixedClock.instant());
        assertThatThrownBy(() -> stateMachine.applyTransition(confirmed, AppointmentStatus.COMPLETED, fixedClock, zone))
                .isInstanceOf(ConflictException.class);

        Appointment inConsult = createAppointment(AppointmentStatus.IN_CONSULTATION, fixedClock.instant().minus(15, ChronoUnit.MINUTES), fixedClock.instant().plus(15, ChronoUnit.MINUTES));
        stateMachine.applyTransition(inConsult, AppointmentStatus.COMPLETED, fixedClock, zone);
        assertThat(inConsult.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(inConsult.getCompletedAt()).isEqualTo(fixedClock.instant());
    }

    @Test
    @DisplayName("Terminal states cannot transition to any new state")
    void terminalStatesRejectTransitions() {
        Appointment completed = createAppointment(AppointmentStatus.COMPLETED, fixedClock.instant().minus(1, ChronoUnit.HOURS), fixedClock.instant().minus(30, ChronoUnit.MINUTES));
        assertThatThrownBy(() -> stateMachine.applyTransition(completed, AppointmentStatus.CANCELLED, fixedClock, zone))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot change status of appointment in terminal state");

        Appointment cancelled = createAppointment(AppointmentStatus.CANCELLED, fixedClock.instant(), fixedClock.instant().plus(30, ChronoUnit.MINUTES));
        assertThatThrownBy(() -> stateMachine.applyTransition(cancelled, AppointmentStatus.CONFIRMED, fixedClock, zone))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot change status of appointment in terminal state");
    }
}

