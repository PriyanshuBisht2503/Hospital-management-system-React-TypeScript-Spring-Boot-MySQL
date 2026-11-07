package com.hms.api.service;

import com.hms.api.domain.*;
import com.hms.api.dto.AppointmentResponseDto;
import com.hms.api.dto.CancelAppointmentRequest;
import com.hms.api.dto.PageResponse;
import com.hms.api.event.AppointmentCancelledEvent;
import com.hms.api.exception.BadRequestException;
import com.hms.api.exception.ConflictException;
import com.hms.api.exception.ResourceNotFoundException;
import com.hms.api.repository.AppointmentRepository;
import com.hms.api.repository.DoctorRepository;
import com.hms.api.repository.PatientRepository;
import com.hms.api.security.SecurityUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class AppointmentLifecycleService {

    public static final int DEFAULT_PAGE_SIZE = 6;
    public static final int MAX_PAGE_SIZE = 100;

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentStateMachine stateMachine;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final ZoneId hospitalZone;

    public AppointmentLifecycleService(
            AppointmentRepository appointmentRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            AppointmentStateMachine stateMachine,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${app.timezone:Asia/Kolkata}") String timezone) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.stateMachine = stateMachine;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.hospitalZone = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponseDto> getAppointments(
            Long doctorId,
            Long branchId,
            AppointmentStatus status,
            Boolean needsRescheduling,
            Long patientId,
            Instant from,
            Instant to,
            int page,
            int size,
            SecurityUser currentUser
    ) {
        int pageSize = (size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int pageNumber = Math.max(0, page);

        Long effectiveDoctorId = doctorId;
        Long effectivePatientId = patientId;

        if (currentUser.getRole() == Role.PATIENT) {
            Optional<Patient> p = patientRepository.findByUserId(currentUser.getId());
            if (p.isEmpty()) {
                return PageResponse.from(Page.empty(PageRequest.of(pageNumber, pageSize)));
            }
            effectivePatientId = p.get().getId();
        } else if (currentUser.getRole() == Role.DOCTOR) {
            Optional<Doctor> d = doctorRepository.findByUserId(currentUser.getId());
            if (d.isEmpty()) {
                return PageResponse.from(Page.empty(PageRequest.of(pageNumber, pageSize)));
            }
            effectiveDoctorId = d.get().getId();
        }

        Page<Appointment> pageResult = appointmentRepository.searchAppointments(
                effectiveDoctorId,
                branchId,
                status,
                needsRescheduling,
                effectivePatientId,
                from,
                to,
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.ASC, "startTime"))
        );

        boolean maskPatientContact = currentUser.getRole() == Role.DOCTOR;
        Page<AppointmentResponseDto> dtoPage = pageResult.map(a -> AppointmentResponseDto.fromEntity(a, maskPatientContact, clock));
        return PageResponse.from(dtoPage);
    }

    @Transactional(readOnly = true)
    public AppointmentResponseDto getAppointmentById(Long id, SecurityUser currentUser, String clientIp) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));

        verifyReadAccess(a, currentUser);

        // Rule 9: Audited when reading appointment details that expose patient data
        auditLogService.log(currentUser.getId(), "VIEW_PATIENT", "APPOINTMENT", String.valueOf(id), clientIp, null);

        boolean maskPatientContact = currentUser.getRole() == Role.DOCTOR;
        return AppointmentResponseDto.fromEntity(a, maskPatientContact, clock);
    }

    @Transactional
    public AppointmentResponseDto confirmAppointment(Long id, SecurityUser currentUser, String clientIp) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));

        stateMachine.applyTransition(a, AppointmentStatus.CONFIRMED, clock, hospitalZone);
        Appointment saved = appointmentRepository.saveAndFlush(a);

        auditLogService.log(currentUser.getId(), "CONFIRM_APPOINTMENT", "APPOINTMENT", String.valueOf(id), clientIp, null);
        boolean maskPatientContact = currentUser.getRole() == Role.DOCTOR;
        return AppointmentResponseDto.fromEntity(saved, maskPatientContact, clock);
    }

    @Transactional
    public AppointmentResponseDto checkInAppointment(Long id, SecurityUser currentUser, String clientIp) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));

        stateMachine.applyTransition(a, AppointmentStatus.CHECKED_IN, clock, hospitalZone);
        Appointment saved = appointmentRepository.saveAndFlush(a);

        auditLogService.log(currentUser.getId(), "CHECK_IN_APPOINTMENT", "APPOINTMENT", String.valueOf(id), clientIp, null);
        boolean maskPatientContact = currentUser.getRole() == Role.DOCTOR;
        return AppointmentResponseDto.fromEntity(saved, maskPatientContact, clock);
    }

    @Transactional
    public AppointmentResponseDto startConsultation(Long id, SecurityUser currentUser, String clientIp) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));

        if (currentUser.getRole() == Role.DOCTOR) {
            verifyDoctorMatch(a, currentUser);
        }

        stateMachine.applyTransition(a, AppointmentStatus.IN_CONSULTATION, clock, hospitalZone);
        Appointment saved = appointmentRepository.saveAndFlush(a);

        auditLogService.log(currentUser.getId(), "START_CONSULTATION", "APPOINTMENT", String.valueOf(id), clientIp, null);
        boolean maskPatientContact = currentUser.getRole() == Role.DOCTOR;
        return AppointmentResponseDto.fromEntity(saved, maskPatientContact, clock);
    }

    @Transactional
    public AppointmentResponseDto completeAppointment(Long id, SecurityUser currentUser, String clientIp) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));

        if (currentUser.getRole() == Role.DOCTOR) {
            verifyDoctorMatch(a, currentUser);
        }

        stateMachine.applyTransition(a, AppointmentStatus.COMPLETED, clock, hospitalZone);
        Appointment saved = appointmentRepository.saveAndFlush(a);

        auditLogService.log(currentUser.getId(), "COMPLETE_APPOINTMENT", "APPOINTMENT", String.valueOf(id), clientIp, null);
        boolean maskPatientContact = currentUser.getRole() == Role.DOCTOR;
        return AppointmentResponseDto.fromEntity(saved, maskPatientContact, clock);
    }

    @Transactional
    public AppointmentResponseDto markNoShow(Long id, SecurityUser currentUser, String clientIp) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));

        if (currentUser.getRole() == Role.DOCTOR) {
            verifyDoctorMatch(a, currentUser);
        }

        stateMachine.applyTransition(a, AppointmentStatus.NO_SHOW, clock, hospitalZone);
        Appointment saved = appointmentRepository.saveAndFlush(a);

        auditLogService.log(currentUser.getId(), "NO_SHOW_APPOINTMENT", "APPOINTMENT", String.valueOf(id), clientIp, null);
        boolean maskPatientContact = currentUser.getRole() == Role.DOCTOR;
        return AppointmentResponseDto.fromEntity(saved, maskPatientContact, clock);
    }

    @Transactional
    public AppointmentResponseDto cancelAppointment(Long id, CancelAppointmentRequest request, SecurityUser currentUser, String clientIp) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));

        if (currentUser.getRole() == Role.PATIENT) {
            verifyPatientMatch(a, currentUser);
            if (a.getStatus() != AppointmentStatus.BOOKED && a.getStatus() != AppointmentStatus.CONFIRMED) {
                throw new ConflictException("Patients can only cancel BOOKED or CONFIRMED appointments");
            }
            if (clock.instant().isAfter(a.getStartTime())) {
                throw new ConflictException("Patients cannot cancel appointments after the scheduled start time");
            }
        } else if (currentUser.getRole() == Role.DOCTOR) {
            verifyDoctorMatch(a, currentUser);
        }

        if (request.reason() == null || request.reason().trim().isEmpty()) {
            throw new BadRequestException("Cancellation reason is mandatory");
        }

        stateMachine.applyTransition(a, AppointmentStatus.CANCELLED, clock, hospitalZone);
        a.setCancelReason(request.reason().trim());
        a.setCancelledBy(currentUser.getUsername());
        Appointment saved = appointmentRepository.saveAndFlush(a);
        eventPublisher.publishEvent(new AppointmentCancelledEvent(saved.getId()));

        auditLogService.log(currentUser.getId(), "CANCEL_APPOINTMENT", "APPOINTMENT", String.valueOf(id), clientIp, "Reason: " + request.reason().trim());
        boolean maskPatientContact = currentUser.getRole() == Role.DOCTOR;
        return AppointmentResponseDto.fromEntity(saved, maskPatientContact, clock);
    }

    /**
     * Patient-only "my appointments": newest first, optional status and upcoming-only filters.
     */
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponseDto> getMyAppointments(
            AppointmentStatus status, Boolean upcoming, int page, int size, SecurityUser currentUser) {
        int pageSize = (size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int pageNumber = Math.max(0, page);

        Optional<Patient> patient = patientRepository.findByUserId(currentUser.getId());
        if (patient.isEmpty()) {
            return PageResponse.from(Page.empty(PageRequest.of(pageNumber, pageSize)));
        }

        Instant from = Boolean.TRUE.equals(upcoming) ? clock.instant() : null;
        Page<Appointment> pageResult = appointmentRepository.searchAppointments(
                null,
                null,
                status,
                null,
                patient.get().getId(),
                from,
                null,
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "startTime"))
        );

        Page<AppointmentResponseDto> dtoPage = pageResult.map(a -> AppointmentResponseDto.fromEntity(a, false, clock));
        return PageResponse.from(dtoPage);
    }

    /**
     * Reusable ownership assertion for endpoints that resolve the appointment in another service
     * (e.g. reschedule). Keeps 404 cloaking identical to read access.
     */
    @Transactional(readOnly = true)
    public void assertPatientOwnership(Long appointmentId, SecurityUser currentUser) {
        Appointment a = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));
        verifyPatientMatch(a, currentUser);
    }

    private void verifyReadAccess(Appointment a, SecurityUser currentUser) {
        if (currentUser.getRole() == Role.PATIENT) {
            verifyPatientMatch(a, currentUser);
        } else if (currentUser.getRole() == Role.DOCTOR) {
            verifyDoctorMatch(a, currentUser);
        }
    }

    private void verifyPatientMatch(Appointment a, SecurityUser currentUser) {
        if (a.getPatient().getUser() == null || !a.getPatient().getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Appointment not found: " + a.getId());
        }
    }

    private void verifyDoctorMatch(Appointment a, SecurityUser currentUser) {
        if (a.getDoctor().getUser() == null || !a.getDoctor().getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Appointment not found: " + a.getId());
        }
    }
}

