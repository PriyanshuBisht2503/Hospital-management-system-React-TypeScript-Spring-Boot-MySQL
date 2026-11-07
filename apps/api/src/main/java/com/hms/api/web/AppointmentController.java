package com.hms.api.web;

import com.hms.api.domain.Appointment;
import com.hms.api.domain.AppointmentStatus;
import com.hms.api.domain.Patient;
import com.hms.api.domain.Role;
import com.hms.api.dto.*;
import com.hms.api.exception.BadRequestException;
import com.hms.api.exception.ResourceNotFoundException;
import com.hms.api.repository.PatientRepository;
import com.hms.api.security.ClientIpResolver;
import com.hms.api.security.RateLimiterService;
import com.hms.api.security.SecurityUser;
import com.hms.api.service.AppointmentBookingService;
import com.hms.api.service.AppointmentLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Appointments", description = "Appointment booking, rescheduling, and lifecycle management. All API timestamps are ISO-8601 UTC instants.")
public class AppointmentController {

    private final AppointmentBookingService bookingService;
    private final AppointmentLifecycleService lifecycleService;
    private final PatientRepository patientRepository;
    private final ClientIpResolver clientIpResolver;
    private final RateLimiterService rateLimiterService;
    private final Clock clock;

    public AppointmentController(
            AppointmentBookingService bookingService,
            AppointmentLifecycleService lifecycleService,
            PatientRepository patientRepository,
            ClientIpResolver clientIpResolver,
            RateLimiterService rateLimiterService,
            Clock clock) {
        this.bookingService = bookingService;
        this.lifecycleService = lifecycleService;
        this.patientRepository = patientRepository;
        this.clientIpResolver = clientIpResolver;
        this.rateLimiterService = rateLimiterService;
        this.clock = clock;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'PATIENT')")
    @Operation(summary = "Book an appointment", description = "Book appointment into an open slot. Timestamps must be ISO-8601 UTC. Staff bookings are immediately CONFIRMED; patient bookings start as BOOKED.")
    public ResponseEntity<AppointmentResponseDto> bookAppointment(
            @Valid @RequestBody BookAppointmentRequest request,
            @AuthenticationPrincipal SecurityUser currentUser
    ) {
        rateLimiterService.checkBookingLimit("user:" + currentUser.getId());

        if (currentUser.getRole() == Role.PATIENT) {
            Patient patient = patientRepository.findById(request.patientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + request.patientId()));
            if (patient.getUser() == null || !patient.getUser().getId().equals(currentUser.getId())) {
                throw new ResourceNotFoundException("Patient not found: " + request.patientId());
            }
        }

        boolean maskPatientContact = currentUser.getRole() == Role.DOCTOR;
        AppointmentResponseDto response = bookingService.bookAppointment(
                request.patientId(),
                request.doctorId(),
                request.branchId(),
                request.startTime(),
                request.endTime(),
                request.type(),
                request.reasonForVisit(),
                currentUser.getRole(),
                currentUser.getUsername(),
                maskPatientContact
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR', 'PATIENT')")
    @Operation(summary = "List appointments", description = "Paginated appointment search. Role-scoped: Patients only see their own; Doctors only see theirs. Default page size is 6.")
    public ResponseEntity<PageResponse<AppointmentResponseDto>> getAppointments(
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Boolean needsRescheduling,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @AuthenticationPrincipal SecurityUser currentUser
    ) {
        return ResponseEntity.ok(lifecycleService.getAppointments(
                doctorId, branchId, status, needsRescheduling, patientId, from, to, page, size, currentUser
        ));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('PATIENT')")
    @Operation(summary = "List my appointments", description = "Patient self-service list, newest first. Optional status and upcoming=true filters.")
    public ResponseEntity<PageResponse<AppointmentResponseDto>> getMyAppointments(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Boolean upcoming,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @AuthenticationPrincipal SecurityUser currentUser
    ) {
        return ResponseEntity.ok(lifecycleService.getMyAppointments(status, upcoming, page, size, currentUser));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR', 'PATIENT')")
    @Operation(summary = "Get appointment details", description = "Retrieve appointment details. Audited with VIEW_PATIENT. Doctor callers receive masked patient phone/email.")
    public ResponseEntity<AppointmentResponseDto> getAppointmentById(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        return ResponseEntity.ok(lifecycleService.getAppointmentById(id, currentUser, clientIp));
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Confirm appointment", description = "Transition BOOKED appointment to CONFIRMED (staff only).")
    public ResponseEntity<AppointmentResponseDto> confirmAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        return ResponseEntity.ok(lifecycleService.confirmAppointment(id, currentUser, clientIp));
    }

    @PatchMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Check in patient", description = "Check in confirmed patient. Permitted strictly on the scheduled appointment day.")
    public ResponseEntity<AppointmentResponseDto> checkInAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        return ResponseEntity.ok(lifecycleService.checkInAppointment(id, currentUser, clientIp));
    }

    @PatchMapping("/{id}/start-consultation")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(summary = "Start consultation", description = "Transition checked-in appointment to IN_CONSULTATION.")
    public ResponseEntity<AppointmentResponseDto> startConsultation(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        return ResponseEntity.ok(lifecycleService.startConsultation(id, currentUser, clientIp));
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(summary = "Complete consultation", description = "Complete appointment from IN_CONSULTATION state.")
    public ResponseEntity<AppointmentResponseDto> completeAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        return ResponseEntity.ok(lifecycleService.completeAppointment(id, currentUser, clientIp));
    }

    @PatchMapping("/{id}/no-show")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    @Operation(summary = "Mark as no-show", description = "Mark confirmed appointment as NO_SHOW (strictly after scheduled start time).")
    public ResponseEntity<AppointmentResponseDto> markNoShow(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        return ResponseEntity.ok(lifecycleService.markNoShow(id, currentUser, clientIp));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR', 'PATIENT')")
    @Operation(summary = "Cancel appointment", description = "Cancel appointment with mandatory reason. Patients can only cancel future appointments.")
    public ResponseEntity<AppointmentResponseDto> cancelAppointment(
            @PathVariable Long id,
            @Valid @RequestBody CancelAppointmentRequest cancelRequest,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        return ResponseEntity.ok(lifecycleService.cancelAppointment(id, cancelRequest, currentUser, clientIp));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'PATIENT')")
    @Operation(summary = "Reschedule appointment", description = "Move appointment to a new slot in one atomic transaction. Old appointment is marked RESCHEDULED, freeing the slot.")
    public ResponseEntity<AppointmentResponseDto> rescheduleAppointment(
            @PathVariable Long id,
            @Valid @RequestBody RescheduleAppointmentRequest rescheduleRequest,
            @AuthenticationPrincipal SecurityUser currentUser
    ) {
        if (currentUser.getRole() == Role.PATIENT) {
            lifecycleService.assertPatientOwnership(id, currentUser);
        }

        boolean maskPatientContact = currentUser.getRole() == Role.DOCTOR;
        AppointmentResponseDto response = bookingService.rescheduleAppointment(
                id,
                rescheduleRequest.newStartTime(),
                rescheduleRequest.newEndTime(),
                rescheduleRequest.newBranchId(),
                rescheduleRequest.reason(),
                currentUser.getRole(),
                currentUser.getUsername(),
                maskPatientContact
        );

        return ResponseEntity.ok(response);
    }
}
