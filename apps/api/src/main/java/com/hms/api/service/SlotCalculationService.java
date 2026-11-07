package com.hms.api.service;

import com.hms.api.domain.Appointment;
import com.hms.api.domain.DoctorSchedule;
import com.hms.api.domain.ScheduleException;
import com.hms.api.domain.ScheduleExceptionType;
import com.hms.api.dto.AvailableSlotDto;
import com.hms.api.repository.AppointmentRepository;
import com.hms.api.repository.DoctorScheduleRepository;
import com.hms.api.repository.ScheduleExceptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class SlotCalculationService {

    private final DoctorScheduleRepository scheduleRepository;
    private final ScheduleExceptionRepository exceptionRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;
    private final ZoneId hospitalZone;
    private final int maxAdvanceDays;
    private final int minLeadMinutes;

    public SlotCalculationService(
            DoctorScheduleRepository scheduleRepository,
            ScheduleExceptionRepository exceptionRepository,
            AppointmentRepository appointmentRepository,
            Clock clock,
            @Value("${app.timezone:Asia/Kolkata}") String timezone,
            @Value("${app.appointment.max-advance-days:60}") int maxAdvanceDays,
            @Value("${app.appointment.min-lead-minutes:30}") int minLeadMinutes) {
        this.scheduleRepository = scheduleRepository;
        this.exceptionRepository = exceptionRepository;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
        this.hospitalZone = ZoneId.of(timezone);
        this.maxAdvanceDays = maxAdvanceDays;
        this.minLeadMinutes = minLeadMinutes;
    }

    public List<AvailableSlotDto> calculateAvailableSlots(Long doctorId, LocalDate startDate, LocalDate endDate, Long branchId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), hospitalZone);
        if (startDate.isBefore(today)) {
            startDate = today;
        }

        LocalDate maxDate = today.plusDays(maxAdvanceDays);
        if (endDate.isAfter(maxDate)) {
            endDate = maxDate;
        }

        if (endDate.isBefore(startDate)) {
            return Collections.emptyList();
        }

        // Cap window to 31 days max for range queries
        if (ChronoUnit.DAYS.between(startDate, endDate) > 31) {
            endDate = startDate.plusDays(31);
        }

        Instant rangeStart = startDate.atStartOfDay(hospitalZone).toInstant();
        Instant rangeEnd = endDate.plusDays(1).atStartOfDay(hospitalZone).toInstant();

        // 1. Fetch schedules, exceptions, and active appointments
        List<DoctorSchedule> schedules = scheduleRepository.findByDoctorIdAndIsActiveTrue(doctorId);
        List<ScheduleException> exceptions = exceptionRepository.findByDoctorIdAndDateBetween(doctorId, startDate, endDate);
        List<Appointment> activeAppts = appointmentRepository.findActiveByDoctorInRange(doctorId, rangeStart, rangeEnd);

        Map<LocalDate, List<ScheduleException>> exceptionsByDate = new HashMap<>();
        for (ScheduleException ex : exceptions) {
            exceptionsByDate.computeIfAbsent(ex.getDate(), d -> new ArrayList<>()).add(ex);
        }

        Instant now = clock.instant();
        Instant minLeadInstant = now.plus(minLeadMinutes, ChronoUnit.MINUTES);

        List<AvailableSlotDto> result = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DayOfWeek dow = date.getDayOfWeek();
            List<ScheduleException> dateExceptions = exceptionsByDate.getOrDefault(date, Collections.emptyList());

            // Check full-day leave or holiday
            boolean fullDayLeave = dateExceptions.stream()
                    .anyMatch(ex -> (ex.getType() == ScheduleExceptionType.LEAVE || ex.getType() == ScheduleExceptionType.HOLIDAY)
                            && ex.getStartTime() == null && ex.getEndTime() == null);
            if (fullDayLeave) {
                continue;
            }

            LocalDate currentDate = date;
            List<DoctorSchedule> matchingSchedules = schedules.stream()
                    .filter(s -> s.getDayOfWeek() == dow)
                    .filter(s -> !currentDate.isBefore(s.getValidFrom()))
                    .filter(s -> s.getValidTo() == null || !currentDate.isAfter(s.getValidTo()))
                    .filter(s -> branchId == null || s.getBranch().getId().equals(branchId))
                    .toList();

            for (DoctorSchedule sched : matchingSchedules) {
                int slotMinutes = sched.getSlotMinutes();
                LocalTime current = sched.getStartTime();
                LocalTime end = sched.getEndTime();

                while (!current.plusMinutes(slotMinutes).isAfter(end)) {
                    LocalTime slotStartLocal = current;
                    LocalTime slotEndLocal = current.plusMinutes(slotMinutes);
                    current = slotEndLocal;

                    // Check break
                    if (sched.getBreakStart() != null && sched.getBreakEnd() != null) {
                        if (slotStartLocal.isBefore(sched.getBreakEnd()) && slotEndLocal.isAfter(sched.getBreakStart())) {
                            continue;
                        }
                    }

                    // Check partial leave on date
                    boolean overlapsLeave = dateExceptions.stream()
                            .filter(ex -> ex.getType() == ScheduleExceptionType.LEAVE || ex.getType() == ScheduleExceptionType.HOLIDAY)
                            .anyMatch(ex -> ex.getStartTime() != null && ex.getEndTime() != null
                                    && slotStartLocal.isBefore(ex.getEndTime()) && slotEndLocal.isAfter(ex.getStartTime()));
                    if (overlapsLeave) {
                        continue;
                    }

                    Instant slotStart = LocalDateTime.of(currentDate, slotStartLocal).atZone(hospitalZone).toInstant();
                    Instant slotEnd = LocalDateTime.of(currentDate, slotEndLocal).atZone(hospitalZone).toInstant();

                    // Must be after min lead time
                    if (slotStart.isBefore(minLeadInstant)) {
                        continue;
                    }

                    // Must not overlap any active appointment
                    boolean overlapsAppointment = activeAppts.stream()
                            .anyMatch(a -> slotStart.isBefore(a.getEndTime()) && slotEnd.isAfter(a.getStartTime()));
                    if (overlapsAppointment) {
                        continue;
                    }

                    result.add(new AvailableSlotDto(
                            doctorId,
                            sched.getBranch().getId(),
                            slotStart,
                            slotEnd,
                            slotMinutes
                    ));
                }
            }

            // Check EXTRA_HOURS exceptions
            for (ScheduleException ex : dateExceptions) {
                if (ex.getType() == ScheduleExceptionType.EXTRA_HOURS && ex.getStartTime() != null && ex.getEndTime() != null) {
                    if (matchingSchedules.isEmpty()) continue;
                    DoctorSchedule baseSched = matchingSchedules.get(0);
                    int slotMinutes = baseSched.getSlotMinutes();
                    LocalTime current = ex.getStartTime();
                    LocalTime end = ex.getEndTime();

                    while (!current.plusMinutes(slotMinutes).isAfter(end)) {
                        LocalTime slotStartLocal = current;
                        LocalTime slotEndLocal = current.plusMinutes(slotMinutes);
                        current = slotEndLocal;

                        Instant slotStart = LocalDateTime.of(currentDate, slotStartLocal).atZone(hospitalZone).toInstant();
                        Instant slotEnd = LocalDateTime.of(currentDate, slotEndLocal).atZone(hospitalZone).toInstant();

                        if (slotStart.isBefore(minLeadInstant)) continue;

                        boolean overlapsAppointment = activeAppts.stream()
                                .anyMatch(a -> slotStart.isBefore(a.getEndTime()) && slotEnd.isAfter(a.getStartTime()));
                        if (overlapsAppointment) continue;

                        result.add(new AvailableSlotDto(
                                doctorId,
                                baseSched.getBranch().getId(),
                                slotStart,
                                slotEnd,
                                slotMinutes
                        ));
                    }
                }
            }
        }

        result.sort(Comparator.comparing(AvailableSlotDto::startTime));
        return result;
    }

    public boolean isSlotOpen(Long doctorId, Instant startTime, Instant endTime, Long branchId) {
        LocalDate date = LocalDate.ofInstant(startTime, hospitalZone);
        List<AvailableSlotDto> slots = calculateAvailableSlots(doctorId, date, date, branchId);
        return slots.stream().anyMatch(s -> s.startTime().equals(startTime) && s.endTime().equals(endTime));
    }

    public ZoneId getHospitalZone() {
        return hospitalZone;
    }
}

