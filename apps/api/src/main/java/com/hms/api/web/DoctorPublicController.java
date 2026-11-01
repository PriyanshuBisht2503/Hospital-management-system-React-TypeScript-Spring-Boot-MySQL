package com.hms.api.web;

import com.hms.api.dto.DoctorPublicDto;
import com.hms.api.dto.PageResponse;
import com.hms.api.service.DoctorService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctors")
public class DoctorPublicController {

    private final DoctorService doctorService;

    public DoctorPublicController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<DoctorPublicDto>> listPublicDoctors(
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "user.fullName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(doctorService.getPublicDoctors(specialtyId, branchId, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DoctorPublicDto> getPublicDoctorById(@PathVariable Long id) {
        return ResponseEntity.ok(doctorService.getPublicDoctorById(id));
    }
}

