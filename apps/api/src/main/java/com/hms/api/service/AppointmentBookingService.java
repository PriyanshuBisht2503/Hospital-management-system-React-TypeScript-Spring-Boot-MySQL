package com.hms.api.service;

import com.hms.api.domain.*;
import com.hms.api.dto.AppointmentResponseDto;
import com.hms.api.event.AppointmentBookedEvent;
import com.hms.api.event.AppointmentRescheduledEvent;
import com.hms.api.exception.BadRequestException;
import com.hms.api.exception.ConflictException;
import com.hms.api.exception.ResourceNotFoundException;
import com.hms.api.repository.AppointmentRepository;
import com.hms.api.repository.BranchRepository;
import com.hms.api.repository.DoctorRepository;
import com.hms.api.repository.PatientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
public class AppointmentBookingService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final BranchRepository branchRepository;
    private final SlotCalculationService slotCalculationService;
    private final AppointmentStateMachine stateMachine;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final ZoneId hospitalZone;

    public AppointmentBookingService(
            AppointmentRepository appointmentRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            BranchRepository branchRepository,
            SlotCalculationService slotCalculationService,
            AppointmentStateMachine stateMachine,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${app.timezone:Asia/Kolkata}") String timezone) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.branchRepository = branchRepository;
        this.slotCalculationService = slotCalculationService;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.hospitalZone = ZoneId.of(timezone);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponseDto bookAppointment(
            Long patientId,
            Long doctorId,
            Long branchId,
            Instant startTime,
            Instant endTime,
            AppointmentType type,
            String reasonForVisit,
            String createdByRole,
            String createdByUser
    ) {
        return bookAppointment(patientId, doctorId, branchId, startTime, endTime, type, reasonForVisit, createdByRole, createdByUser, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponseDto bookAppointment(
            Long patientId,
            Long doctorId,
            Long branchId,
            Instant startTime,
            Instant endTime,
            AppointmentType type,
            String reasonForVisit,
            String createdByRole,
            String createdByUser,
            boolean maskPatientContact
    ) {
        // 1. Pessimistic lock on the doctor row to serialize concurrent booking attempts
        Doctor doctor = doctorRepository.findByIdForUpdate(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));

        if (!doctor.isActive()) {
            throw new ConflictException("Doctor is currently inactive and cannot accept appointments");
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + patientId));

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));

        if (!branch.isActive()) {
            throw new ConflictException("Branch is currently inactive and cannot accept appointments");
        }

        // 2. Booking Rules & Slot Validation
        if (type == AppointmentType.WALK_IN) {
            boolean isStaff = "ADMIN".equals(createdByRole) || "RECEPTIONIST".equals(createdByRole);
            if (!isStaff) {
                throw new BadRequestException("Walk-in appointments can only be booked by hospital staff (ADMIN or RECEPTIONIST)");
            }
            if (reasonForVisit == null || reasonForVisit.trim().isEmpty()) {
                throw new BadRequestException("Walk-in appointments require a mandatory reason for visit");
            }
        } else {
            boolean slotOpen = slotCalculationService.isSlotOpen(doctorId, startTime, endTime, branchId);
            if (!slotOpen) {
                throw new ConflictException("Requested appointment time does not match an open, available slot");
            }
        }

        // 3. Application Overlap Checks
        List<Appointment> docOverlaps = appointmentRepository.findDoctorOverlappingAppointments(doctorId, startTime, endTime, null);
        if (!docOverlaps.isEmpty()) {
            throw new ConflictException("Doctor already has an active appointment overlapping this time window");
        }

        List<Appointment> patOverlaps = appointmentRepository.findPatientOverlappingAppointments(patientId, startTime, endTime, null);
        if (!patOverlaps.isEmpty()) {
            throw new ConflictException("Patient already has an active appointment overlapping this time window");
        }

        // 4. Initial Status Rule: Staff created -> CONFIRMED; Patient created -> BOOKED
        AppointmentStatus initialStatus;
        Instant confirmedAt = null;
        if ("ADMIN".equals(createdByRole) || "RECEPTIONIST".equals(createdByRole)) {
            initialStatus = AppointmentStatus.CONFIRMED;
            confirmedAt = clock.instant();
        } else {
            initialStatus = AppointmentStatus.BOOKED;
        }

        Appointment appointment = new Appointment(patient, doctor, branch, startTime, endTime, type, initialStatus);
        appointment.setReasonForVisit(reasonForVisit);
        appointment.setCreatedBy(createdByUser);
        appointment.setConfirmedAt(confirmedAt);

        // 5. Persist with database unique key protection on active_booking_slot
        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        eventPublisher.publishEvent(new AppointmentBookedEvent(saved.getId()));
        return AppointmentResponseDto.fromEntity(saved, maskPatientContact, clock);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponseDto rescheduleAppointment(
            Long appointmentId,
            Instant newStartTime,
            Instant newEndTime,
            Long newBranchId,
            String rescheduleReason,
            String requestedByRole,
            String requestedByUser
    ) {
        return rescheduleAppointment(appointmentId, newStartTime, newEndTime, newBranchId, rescheduleReason, requestedByRole, requestedByUser, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponseDto rescheduleAppointment(
            Long appointmentId,
            Instant newStartTime,
            Instant newEndTime,
            Long newBranchId,
            String rescheduleReason,
            String requestedByRole,
            String requestedByUser,
            boolean maskPatientContact
    ) {
        // 1. Resolve doctor and take exclusive per-doctor lock FIRST to serialize all bookings/reschedules for this doctor
        Long doctorId = appointmentRepository.findDoctorIdByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

        Doctor doctor = doctorRepository.findByIdForUpdate(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));

        // 2. Load appointment under doctor lock
        Appointment existing = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

        // 3. Validate transition to RESCHEDULED
        stateMachine.validateTransition(existing, AppointmentStatus.RESCHEDULED, clock, hospitalZone);

        Long targetBranchId = newBranchId != null ? newBranchId : existing.getBranch().getId();
        Branch branch = branchRepository.findById(targetBranchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + targetBranchId));
        if (!branch.isActive()) {
            throw new ConflictException("Branch is currently inactive");
        }

        // 3. Validate new slot is open
        boolean slotOpen = slotCalculationService.isSlotOpen(existing.getDoctor().getId(), newStartTime, newEndTime, targetBranchId);
        if (!slotOpen) {
            throw new ConflictException("Requested reschedule slot is not open or available");
        }

        // 4. Overlap checks
        List<Appointment> docOverlaps = appointmentRepository.findDoctorOverlappingAppointments(
                existing.getDoctor().getId(), newStartTime, newEndTime, existing.getId());
        if (!docOverlaps.isEmpty()) {
            throw new ConflictException("Doctor already has an active appointment overlapping the new slot");
        }

        List<Appointment> patOverlaps = appointmentRepository.findPatientOverlappingAppointments(
                existing.getPatient().getId(), newStartTime, newEndTime, existing.getId());
        if (!patOverlaps.isEmpty()) {
            throw new ConflictException("Patient already has an active appointment overlapping the new slot");
        }

        // 5. Atomic state update on existing
        existing.setStatus(AppointmentStatus.RESCHEDULED);
        existing.setNeedsRescheduling(false);
        existing.setCancelReason(rescheduleReason);
        existing.setCancelledBy(requestedByUser);
        appointmentRepository.saveAndFlush(existing);

        // 6. Create new appointment linked to old
        Appointment newAppt = new Appointment(
                existing.getPatient(),
                existing.getDoctor(),
                branch,
                newStartTime,
                newEndTime,
                existing.getType(),
                AppointmentStatus.CONFIRMED
        );
        newAppt.setReasonForVisit(existing.getReasonForVisit());
        newAppt.setRescheduledFrom(existing);
        newAppt.setCreatedBy(requestedByUser);
        newAppt.setConfirmedAt(clock.instant());

        Appointment saved = appointmentRepository.saveAndFlush(newAppt);
        eventPublisher.publishEvent(new AppointmentRescheduledEvent(existing.getId(), saved.getId()));
        return AppointmentResponseDto.fromEntity(saved, maskPatientContact, clock);
    }
}
