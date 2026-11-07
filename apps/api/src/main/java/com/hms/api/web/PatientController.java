package com.hms.api.web;

import com.hms.api.dto.CreatePatientRequest;
import com.hms.api.dto.PageResponse;
import com.hms.api.dto.PatientDto;
import com.hms.api.security.ClientIpResolver;
import com.hms.api.security.SecurityUser;
import com.hms.api.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "Patients", description = "Patient records and directory management")
public class PatientController {

    private final PatientService patientService;
    private final ClientIpResolver clientIpResolver;

    public PatientController(PatientService patientService, ClientIpResolver clientIpResolver) {
        this.patientService = patientService;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR', 'PATIENT')")
    @Operation(summary = "Get patient by ID", description = "Retrieve patient profile. Audited with VIEW_PATIENT. DOCTOR callers receive masked phone/email.")
    public ResponseEntity<PatientDto> getPatientById(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        return ResponseEntity.ok(patientService.getPatientById(id, currentUser, clientIp));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    @Operation(summary = "Search patient directory", description = "Paginated patient search by name, phone or email. Audited with SEARCH_PATIENTS.")
    public ResponseEntity<PageResponse<PatientDto>> searchPatients(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        return ResponseEntity.ok(patientService.searchPatients(search, page, size, currentUser, clientIp));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @Operation(summary = "Create patient record", description = "Create a new patient record (e.g. walk-in registration). Requires at least phone or email.")
    public ResponseEntity<PatientDto> createPatient(
            @Valid @RequestBody CreatePatientRequest createRequest,
            @AuthenticationPrincipal SecurityUser currentUser,
            HttpServletRequest request
    ) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        PatientDto created = patientService.createPatient(createRequest, currentUser, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}

