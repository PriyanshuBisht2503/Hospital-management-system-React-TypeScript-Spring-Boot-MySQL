package com.hms.api.web;

import com.hms.api.dto.CreateDoctorScheduleRequest;
import com.hms.api.dto.DoctorScheduleDto;
import com.hms.api.dto.UpdateDoctorScheduleRequest;
import com.hms.api.security.ClientIpResolver;
import com.hms.api.security.SecurityUser;
import com.hms.api.service.DoctorScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/doctors/{doctorId}/schedules")
@Tag(name = "Doctor Schedules", description = "Management of doctor weekly schedule templates")
public class DoctorScheduleController {

    private final DoctorScheduleService scheduleService;
    private final ClientIpResolver clientIpResolver;

    public DoctorScheduleController(DoctorScheduleService scheduleService, ClientIpResolver clientIpResolver) {
        this.scheduleService = scheduleService;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    @Operation(summary = "Get doctor schedules", description = "List weekly schedule templates for a doctor. Doctors can only view their own.")
    public ResponseEntity<List<DoctorScheduleDto>> getDoctorSchedules(
            @PathVariable Long doctorId,
            @AuthenticationPrincipal SecurityUser currentUser
    ) {
        return ResponseEntity.ok(scheduleService.getDoctorSchedules(doctorId, currentUser));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create doctor schedule", description = "Add weekly schedule template for a doctor (Admin only).")
    public ResponseEntity<DoctorScheduleDto> createSchedule(
            @PathVariable Long doctorId,
            @Valid @RequestBody CreateDoctorScheduleRequest req,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        DoctorScheduleDto created = scheduleService.createSchedule(doctorId, req, currentUser, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{scheduleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update doctor schedule", description = "Update schedule template. Cannot deactivate if future active appointments exist.")
    public ResponseEntity<DoctorScheduleDto> updateSchedule(
            @PathVariable Long doctorId,
            @PathVariable Long scheduleId,
            @Valid @RequestBody UpdateDoctorScheduleRequest req,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        return ResponseEntity.ok(scheduleService.updateSchedule(doctorId, scheduleId, req, currentUser, clientIp));
    }

    @DeleteMapping("/{scheduleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate doctor schedule", description = "Deactivate schedule template. Cannot deactivate if future active appointments exist.")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable Long doctorId,
            @PathVariable Long scheduleId,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        scheduleService.deleteSchedule(doctorId, scheduleId, currentUser, clientIp);
        return ResponseEntity.noContent().build();
    }
}

