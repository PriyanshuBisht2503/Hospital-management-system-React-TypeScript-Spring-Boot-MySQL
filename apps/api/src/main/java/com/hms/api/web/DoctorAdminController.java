package com.hms.api.web;

import com.hms.api.dto.DoctorAdminDto;
import com.hms.api.dto.DoctorCreateRequest;
import com.hms.api.dto.DoctorUpdateRequest;
import com.hms.api.dto.MessageResponse;
import com.hms.api.dto.PageResponse;
import com.hms.api.service.DoctorService;
import com.hms.api.dto.FileResponseDto;
import com.hms.api.security.SecurityUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/doctors")
@PreAuthorize("hasRole('ADMIN')")
public class DoctorAdminController {

    private final DoctorService doctorService;

    public DoctorAdminController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<DoctorAdminDto>> listAdminDoctors(
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "user.fullName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(doctorService.getAdminDoctors(specialtyId, branchId, active, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DoctorAdminDto> getAdminDoctorById(@PathVariable Long id) {
        return ResponseEntity.ok(doctorService.getAdminDoctorById(id));
    }

    @PostMapping
    public ResponseEntity<DoctorAdminDto> createDoctor(@Valid @RequestBody DoctorCreateRequest req) {
        DoctorAdminDto created = doctorService.createDoctor(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DoctorAdminDto> updateDoctor(
            @PathVariable Long id,
            @Valid @RequestBody DoctorUpdateRequest req
    ) {
        return ResponseEntity.ok(doctorService.updateDoctor(id, req));
    }

    @PostMapping("/photo")
    public ResponseEntity<FileResponseDto> uploadDoctorPhotoForCreation(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal SecurityUser currentUser
    ) {
        FileResponseDto photo = doctorService.uploadPublicDoctorPhoto(file, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(photo);
    }

    @PostMapping("/{id}/photo")
    public ResponseEntity<FileResponseDto> uploadDoctorPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal SecurityUser currentUser
    ) {
        FileResponseDto photo = doctorService.uploadDoctorPhoto(id, file, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(photo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> deactivateDoctor(@PathVariable Long id) {
        doctorService.deactivateDoctor(id);
        return ResponseEntity.ok(new MessageResponse("Doctor profile deactivated and linked user disabled"));
    }
}

