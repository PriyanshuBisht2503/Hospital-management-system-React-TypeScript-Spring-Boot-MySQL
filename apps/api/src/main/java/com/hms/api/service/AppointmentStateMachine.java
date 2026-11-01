package com.hms.api.service;

import com.hms.api.domain.Appointment;
import com.hms.api.domain.AppointmentStatus;
import com.hms.api.exception.ConflictException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class AppointmentStateMachine {

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> ALLOWED_TRANSITIONS = Map.of(
            AppointmentStatus.BOOKED, EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.CANCELLED),
            AppointmentStatus.CONFIRMED, EnumSet.of(AppointmentStatus.CHECKED_IN, AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW, AppointmentStatus.RESCHEDULED),
            AppointmentStatus.CHECKED_IN, EnumSet.of(AppointmentStatus.IN_CONSULTATION, AppointmentStatus.CANCELLED),
            AppointmentStatus.IN_CONSULTATION, EnumSet.of(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED)
    );

    private static final Set<AppointmentStatus> TERMINAL_STATES = EnumSet.of(
            AppointmentStatus.COMPLETED,
            AppointmentStatus.CANCELLED,
            AppointmentStatus.NO_SHOW,
            AppointmentStatus.RESCHEDULED
    );

    public void validateTransition(Appointment appointment, AppointmentStatus targetStatus, Clock clock, ZoneId hospitalZone) {
        AppointmentStatus current = appointment.getStatus();

        if (current == targetStatus) {
            return;
        }

        if (TERMINAL_STATES.contains(current)) {
            throw new ConflictException("Cannot change status of appointment in terminal state: " + current);
        }

        Set<AppointmentStatus> validNextStates = ALLOWED_TRANSITIONS.get(current);
        if (validNextStates == null || !validNextStates.contains(targetStatus)) {
            throw new ConflictException("Invalid appointment status transition from " + current + " to " + targetStatus);
        }

        Instant now = clock.instant();

        if (targetStatus == AppointmentStatus.CHECKED_IN) {
            LocalDate today = LocalDate.ofInstant(now, hospitalZone);
            LocalDate apptDate = LocalDate.ofInstant(appointment.getStartTime(), hospitalZone);
            if (!today.equals(apptDate)) {
                throw new ConflictException("Check-in is permitted only on the day of the appointment");
            }
        }

        if (targetStatus == AppointmentStatus.NO_SHOW) {
            if (now.isBefore(appointment.getStartTime())) {
                throw new ConflictException("Cannot mark as NO_SHOW before appointment start time");
            }
        }

        if (targetStatus == AppointmentStatus.COMPLETED) {
            if (current != AppointmentStatus.IN_CONSULTATION) {
                throw new ConflictException("Appointment can only be completed from IN_CONSULTATION state");
            }
        }
    }

    public void applyTransition(Appointment appointment, AppointmentStatus targetStatus, Clock clock, ZoneId hospitalZone) {
        validateTransition(appointment, targetStatus, clock, hospitalZone);
        Instant now = clock.instant();
        appointment.setStatus(targetStatus);

        switch (targetStatus) {
            case CONFIRMED -> appointment.setConfirmedAt(now);
            case CHECKED_IN -> appointment.setCheckedInAt(now);
            case IN_CONSULTATION -> appointment.setStartedAt(now);
            case COMPLETED -> appointment.setCompletedAt(now);
            case CANCELLED -> appointment.setCancelledAt(now);
            default -> {}
        }
    }
}

