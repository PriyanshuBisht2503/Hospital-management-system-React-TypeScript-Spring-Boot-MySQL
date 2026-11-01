package com.hms.api.service;

import com.hms.api.domain.Appointment;
import com.hms.api.domain.Branch;
import com.hms.api.domain.Doctor;
import com.hms.api.domain.DoctorSchedule;
import com.hms.api.domain.Role;
import com.hms.api.dto.CreateDoctorScheduleRequest;
import com.hms.api.dto.DoctorScheduleDto;
import com.hms.api.dto.UpdateDoctorScheduleRequest;
import com.hms.api.exception.BadRequestException;
import com.hms.api.exception.ConflictException;
import com.hms.api.exception.ResourceNotFoundException;
import com.hms.api.repository.AppointmentRepository;
import com.hms.api.repository.BranchRepository;
import com.hms.api.repository.DoctorRepository;
import com.hms.api.repository.DoctorScheduleRepository;
import com.hms.api.security.SecurityUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional
public class DoctorScheduleService {

    private final DoctorScheduleRepository scheduleRepository;
    private final DoctorRepository doctorRepository;
    private final BranchRepository branchRepository;
    private final AppointmentRepository appointmentRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final ZoneId hospitalZone;

    public DoctorScheduleService(
            DoctorScheduleRepository scheduleRepository,
            DoctorRepository doctorRepository,
            BranchRepository branchRepository,
            AppointmentRepository appointmentRepository,
            AuditLogService auditLogService,
            Clock clock,
            @Value("${app.timezone:Asia/Kolkata}") String timezone
    ) {
        this.scheduleRepository = scheduleRepository;
        this.doctorRepository = doctorRepository;
        this.branchRepository = branchRepository;
        this.appointmentRepository = appointmentRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.hospitalZone = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public List<DoctorScheduleDto> getDoctorSchedules(Long doctorId, SecurityUser currentUser) {
        verifyDoctorOwnershipOrStaff(doctorId, currentUser);
        return scheduleRepository.findByDoctorId(doctorId).stream()
                .map(DoctorScheduleDto::fromEntity)
                .toList();
    }

    public DoctorScheduleDto createSchedule(Long doctorId, CreateDoctorScheduleRequest req, SecurityUser currentUser, String clientIp) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));

        Branch branch = branchRepository.findById(req.branchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + req.branchId()));

        validateScheduleTimes(req.startTime(), req.endTime(), req.breakStart(), req.breakEnd(), req.validFrom(), req.validTo());

        // Check overlap with active schedules on the same day_of_week
        List<DoctorSchedule> overlaps = scheduleRepository.findOverlappingSchedules(
                doctorId, req.dayOfWeek(), req.startTime(), req.endTime(), null
        );
        for (DoctorSchedule existing : overlaps) {
            LocalDate existingTo = existing.getValidTo() != null ? existing.getValidTo() : LocalDate.MAX;
            LocalDate reqTo = req.validTo() != null ? req.validTo() : LocalDate.MAX;
            if (!req.validFrom().isAfter(existingTo) && !existing.getValidFrom().isAfter(reqTo)) {
                throw new ConflictException("Doctor already has an active schedule on " + req.dayOfWeek() + " overlapping this time window");
            }
        }

        DoctorSchedule schedule = new DoctorSchedule(
                doctor, branch, req.dayOfWeek(), req.startTime(), req.endTime(), req.slotMinutes(), req.validFrom()
        );
        schedule.setBreakStart(req.breakStart());
        schedule.setBreakEnd(req.breakEnd());
        schedule.setValidTo(req.validTo());
        schedule.setActive(true);

        DoctorSchedule saved = scheduleRepository.save(schedule);
        auditLogService.log(
                currentUser.getId(),
                "CREATE_SCHEDULE",
                "SCHEDULE",
                saved.getId().toString(),
                clientIp,
                "Created schedule on " + req.dayOfWeek() + " for doctor " + doctorId
        );

        return DoctorScheduleDto.fromEntity(saved);
    }

    public DoctorScheduleDto updateSchedule(
            Long doctorId, Long scheduleId, UpdateDoctorScheduleRequest req, SecurityUser currentUser, String clientIp
    ) {
        DoctorSchedule schedule = scheduleRepository.findByIdAndDoctorId(scheduleId, doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found: " + scheduleId + " for doctor: " + doctorId));

        Branch branch = branchRepository.findById(req.branchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + req.branchId()));

        validateScheduleTimes(req.startTime(), req.endTime(), req.breakStart(), req.breakEnd(), req.validFrom(), req.validTo());

        // If deactivating, verify no future active appointments on this schedule
        if (req.isActive() != null && !req.isActive() && schedule.isActive()) {
            assertNoFutureActiveAppointments(doctorId, schedule);
        }

        // Check overlap with other active schedules on same day_of_week
        List<DoctorSchedule> overlaps = scheduleRepository.findOverlappingSchedules(
                doctorId, req.dayOfWeek(), req.startTime(), req.endTime(), scheduleId
        );
        for (DoctorSchedule existing : overlaps) {
            LocalDate existingTo = existing.getValidTo() != null ? existing.getValidTo() : LocalDate.MAX;
            LocalDate reqTo = req.validTo() != null ? req.validTo() : LocalDate.MAX;
            if (!req.validFrom().isAfter(existingTo) && !existing.getValidFrom().isAfter(reqTo)) {
                throw new ConflictException("Doctor already has another active schedule on " + req.dayOfWeek() + " overlapping this time window");
            }
        }

        schedule.setBranch(branch);
        schedule.setDayOfWeek(req.dayOfWeek());
        schedule.setStartTime(req.startTime());
        schedule.setEndTime(req.endTime());
        schedule.setSlotMinutes(req.slotMinutes());
        schedule.setBreakStart(req.breakStart());
        schedule.setBreakEnd(req.breakEnd());
        schedule.setValidFrom(req.validFrom());
        schedule.setValidTo(req.validTo());
        if (req.isActive() != null) {
            schedule.setActive(req.isActive());
        }

        DoctorSchedule saved = scheduleRepository.save(schedule);
        auditLogService.log(
                currentUser.getId(),
                "UPDATE_SCHEDULE",
                "SCHEDULE",
                saved.getId().toString(),
                clientIp,
                "Updated schedule " + scheduleId + " for doctor " + doctorId
        );

        return DoctorScheduleDto.fromEntity(saved);
    }

    public void deleteSchedule(Long doctorId, Long scheduleId, SecurityUser currentUser, String clientIp) {
        DoctorSchedule schedule = scheduleRepository.findByIdAndDoctorId(scheduleId, doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found: " + scheduleId + " for doctor: " + doctorId));

        assertNoFutureActiveAppointments(doctorId, schedule);

        schedule.setActive(false);
        scheduleRepository.save(schedule);

        auditLogService.log(
                currentUser.getId(),
                "DELETE_SCHEDULE",
                "SCHEDULE",
                scheduleId.toString(),
                clientIp,
                "Deactivated schedule " + scheduleId + " for doctor " + doctorId
        );
    }

    private void assertNoFutureActiveAppointments(Long doctorId, DoctorSchedule schedule) {
        List<Appointment> futureAppts = appointmentRepository.findFutureActiveByDoctor(doctorId, clock.instant());
        boolean hasFutureOnSchedule = futureAppts.stream().anyMatch(a -> {
            LocalDate apptDate = LocalDate.ofInstant(a.getStartTime(), hospitalZone);
            return apptDate.getDayOfWeek() == schedule.getDayOfWeek() &&
                   a.getBranch().getId().equals(schedule.getBranch().getId());
        });
        if (hasFutureOnSchedule) {
            throw new ConflictException("Cannot deactivate or delete schedule: doctor has future active appointments scheduled on " + schedule.getDayOfWeek());
        }
    }

    private void validateScheduleTimes(
            java.time.LocalTime start, java.time.LocalTime end,
            java.time.LocalTime breakStart, java.time.LocalTime breakEnd,
            LocalDate validFrom, LocalDate validTo
    ) {
        if (!end.isAfter(start)) {
            throw new BadRequestException("Schedule endTime must be strictly after startTime");
        }
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new BadRequestException("validTo date cannot be before validFrom date");
        }
        if (breakStart != null || breakEnd != null) {
            if (breakStart == null || breakEnd == null) {
                throw new BadRequestException("Both breakStart and breakEnd must be specified if a break is configured");
            }
            if (!breakEnd.isAfter(breakStart)) {
                throw new BadRequestException("breakEnd must be strictly after breakStart");
            }
            if (breakStart.isBefore(start) || breakEnd.isAfter(end)) {
                throw new BadRequestException("Break window must fall within the working hours [startTime, endTime]");
            }
        }
    }

    private void verifyDoctorOwnershipOrStaff(Long doctorId, SecurityUser currentUser) {
        if (currentUser.getRole() == Role.DOCTOR) {
            doctorRepository.findByUserId(currentUser.getId()).ifPresentOrElse(
                    d -> {
                        if (!d.getId().equals(doctorId)) {
                            throw new AccessDeniedException("Access denied: doctors can only access their own schedule");
                        }
                    },
                    () -> {
                        throw new AccessDeniedException("No doctor profile found for current user");
                    }
            );
        }
    }
}

