package com.hms.api.service;

import com.hms.api.domain.Appointment;
import com.hms.api.domain.Doctor;
import com.hms.api.domain.Role;
import com.hms.api.domain.ScheduleException;
import com.hms.api.domain.ScheduleExceptionType;
import com.hms.api.dto.CreateScheduleExceptionRequest;
import com.hms.api.dto.ScheduleExceptionDto;
import com.hms.api.exception.BadRequestException;
import com.hms.api.exception.ConflictException;
import com.hms.api.exception.ResourceNotFoundException;
import com.hms.api.repository.AppointmentRepository;
import com.hms.api.repository.DoctorRepository;
import com.hms.api.repository.ScheduleExceptionRepository;
import com.hms.api.security.SecurityUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional
public class ScheduleExceptionService {

    private final ScheduleExceptionRepository exceptionRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final ZoneId hospitalZone;

    public ScheduleExceptionService(
            ScheduleExceptionRepository exceptionRepository,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            AuditLogService auditLogService,
            Clock clock,
            @Value("${app.timezone:Asia/Kolkata}") String timezone
    ) {
        this.exceptionRepository = exceptionRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.hospitalZone = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public List<ScheduleExceptionDto> getExceptions(Long doctorId, SecurityUser currentUser) {
        verifyDoctorOwnershipOrStaff(doctorId, currentUser);
        return exceptionRepository.findByDoctorIdOrderByDateDesc(doctorId).stream()
                .map(ScheduleExceptionDto::fromEntity)
                .toList();
    }

    public ScheduleExceptionDto createException(
            Long doctorId, CreateScheduleExceptionRequest req, SecurityUser currentUser, String clientIp
    ) {
        verifyDoctorOwnershipOrStaff(doctorId, currentUser);

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));

        if (req.startTime() != null || req.endTime() != null) {
            if (req.startTime() == null || req.endTime() == null) {
                throw new BadRequestException("Both startTime and endTime must be provided for partial-day exceptions");
            }
            if (!req.endTime().isAfter(req.startTime())) {
                throw new BadRequestException("Exception endTime must be strictly after startTime");
            }
        }

        // Conflict check for LEAVE and HOLIDAY
        if (req.type() == ScheduleExceptionType.LEAVE || req.type() == ScheduleExceptionType.HOLIDAY) {
            Instant windowStart = (req.startTime() != null)
                    ? LocalDateTime.of(req.date(), req.startTime()).atZone(hospitalZone).toInstant()
                    : req.date().atStartOfDay(hospitalZone).toInstant();

            Instant windowEnd = (req.endTime() != null)
                    ? LocalDateTime.of(req.date(), req.endTime()).atZone(hospitalZone).toInstant()
                    : req.date().plusDays(1).atStartOfDay(hospitalZone).toInstant();

            List<Appointment> conflicts = appointmentRepository.findDoctorOverlappingAppointments(
                    doctorId, windowStart, windowEnd, null
            );

            if (!conflicts.isEmpty()) {
                if (!req.force()) {
                    List<Long> conflictIds = conflicts.stream().map(Appointment::getId).toList();
                    throw new ConflictException(
                            "Doctor has " + conflicts.size() + " active appointments during this time window (IDs: "
                            + conflictIds + "). Use force=true to proceed and mark them for rescheduling."
                    );
                }

                // If force=true, mark affected appointments with needs_rescheduling=true without cancelling
                for (Appointment appt : conflicts) {
                    appt.setNeedsRescheduling(true);
                    appointmentRepository.save(appt);
                }
            }
        }

        ScheduleException exception = new ScheduleException(doctor, req.date(), req.type(), req.reason());
        exception.setStartTime(req.startTime());
        exception.setEndTime(req.endTime());

        ScheduleException saved = exceptionRepository.save(exception);
        auditLogService.log(
                currentUser.getId(),
                "CREATE_SCHEDULE_EXCEPTION",
                "SCHEDULE_EXCEPTION",
                saved.getId().toString(),
                clientIp,
                "Created " + req.type() + " exception for doctor " + doctorId + " on " + req.date()
        );

        return ScheduleExceptionDto.fromEntity(saved);
    }

    public void deleteException(Long doctorId, Long exceptionId, SecurityUser currentUser, String clientIp) {
        verifyDoctorOwnershipOrStaff(doctorId, currentUser);

        ScheduleException exception = exceptionRepository.findByIdAndDoctorId(exceptionId, doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule exception not found: " + exceptionId + " for doctor: " + doctorId));

        exceptionRepository.delete(exception);

        auditLogService.log(
                currentUser.getId(),
                "DELETE_SCHEDULE_EXCEPTION",
                "SCHEDULE_EXCEPTION",
                exceptionId.toString(),
                clientIp,
                "Deleted exception " + exceptionId + " for doctor " + doctorId
        );
    }

    private void verifyDoctorOwnershipOrStaff(Long doctorId, SecurityUser currentUser) {
        if (currentUser.getRole() == Role.DOCTOR) {
            doctorRepository.findByUserId(currentUser.getId()).ifPresentOrElse(
                    d -> {
                        if (!d.getId().equals(doctorId)) {
                            throw new AccessDeniedException("Access denied: doctors can only access their own exceptions");
                        }
                    },
                    () -> {
                        throw new AccessDeniedException("No doctor profile found for current user");
                    }
            );
        }
    }
}

