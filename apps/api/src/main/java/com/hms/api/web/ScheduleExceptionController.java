package com.hms.api.web;

import com.hms.api.dto.CreateScheduleExceptionRequest;
import com.hms.api.dto.ScheduleExceptionDto;
import com.hms.api.security.ClientIpResolver;
import com.hms.api.security.SecurityUser;
import com.hms.api.service.ScheduleExceptionService;
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
@RequestMapping("/api/admin/doctors/{doctorId}/exceptions")
@Tag(name = "Doctor Exceptions", description = "Management of doctor leaves, holidays, and extra hours")
public class ScheduleExceptionController {

    private final ScheduleExceptionService exceptionService;
    private final ClientIpResolver clientIpResolver;

    public ScheduleExceptionController(ScheduleExceptionService exceptionService, ClientIpResolver clientIpResolver) {
        this.exceptionService = exceptionService;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    @Operation(summary = "Get doctor exceptions", description = "List leaves and holidays for a doctor. Doctors can only view their own.")
    public ResponseEntity<List<ScheduleExceptionDto>> getExceptions(
            @PathVariable Long doctorId,
            @AuthenticationPrincipal SecurityUser currentUser
    ) {
        return ResponseEntity.ok(exceptionService.getExceptions(doctorId, currentUser));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Create doctor exception", description = "Add leave or holiday. If active appointments exist, returns 409 conflict unless force=true.")
    public ResponseEntity<ScheduleExceptionDto> createException(
            @PathVariable Long doctorId,
            @Valid @RequestBody CreateScheduleExceptionRequest req,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        ScheduleExceptionDto created = exceptionService.createException(doctorId, req, currentUser, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{exceptionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @Operation(summary = "Delete doctor exception", description = "Delete a schedule exception (leave or holiday).")
    public ResponseEntity<Void> deleteException(
            @PathVariable Long doctorId,
            @PathVariable Long exceptionId,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        exceptionService.deleteException(doctorId, exceptionId, currentUser, clientIp);
        return ResponseEntity.noContent().build();
    }
}

