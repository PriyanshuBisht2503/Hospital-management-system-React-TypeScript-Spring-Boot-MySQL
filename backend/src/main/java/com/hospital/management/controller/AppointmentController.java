package com.hospital.management.controller;

import com.hospital.management.dto.AppointmentBookRequest;
import com.hospital.management.dto.AppointmentDto;
import com.hospital.management.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Validated
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'PATIENT')")
    public ResponseEntity<AppointmentDto> bookAppointment(@Valid @RequestBody AppointmentBookRequest request) {
        AppointmentDto appointment = appointmentService.bookAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(appointment);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'RECEPTIONIST', 'PATIENT')")
    public ResponseEntity<AppointmentDto> getAppointmentById(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.getAppointmentById(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'PATIENT', 'DOCTOR')")
    public ResponseEntity<Void> cancelAppointment(@PathVariable Long id) {
        appointmentService.cancelAppointment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST') or (hasRole('PATIENT') and #patientId == principal.id)")
    public ResponseEntity<Page<AppointmentDto>> getPatientAppointments(
        @PathVariable Long patientId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("appointmentDate").descending().and(Sort.by("timeSlot").descending()));
        return ResponseEntity.ok(appointmentService.getPatientAppointments(patientId, pageable));
    }

    @GetMapping("/doctor/{doctorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST') or (hasRole('DOCTOR') and #doctorId == principal.id)")
    public ResponseEntity<Page<AppointmentDto>> getDoctorAppointments(
        @PathVariable Long doctorId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("appointmentDate").descending().and(Sort.by("timeSlot").descending()));
        return ResponseEntity.ok(appointmentService.getDoctorAppointments(doctorId, pageable));
    }
}
