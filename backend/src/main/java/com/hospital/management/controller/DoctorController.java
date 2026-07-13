package com.hospital.management.controller;

import com.hospital.management.dto.DoctorDto;
import com.hospital.management.service.DoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
@Validated
@PreAuthorize("isAuthenticated()")
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping("/{id}")
    public ResponseEntity<DoctorDto> getDoctorById(@PathVariable Long id) {
        return ResponseEntity.ok(doctorService.getDoctorById(id));
    }

    @GetMapping
    public ResponseEntity<Page<DoctorDto>> searchDoctors(
        @RequestParam(value = "query", required = false) String query,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestParam(value = "sort", defaultValue = "id,asc") String sort
    ) {
        String[] sortParams = sort.split(",");
        Sort sortOrder = Sort.by(Sort.Direction.fromString(sortParams[1]), sortParams[0]);
        Pageable pageable = PageRequest.of(page, size, sortOrder);
        return ResponseEntity.ok(doctorService.searchDoctors(query, pageable));
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<DoctorDto>> getDoctorsByDepartment(@PathVariable Long departmentId) {
        return ResponseEntity.ok(doctorService.findDoctorsByDepartment(departmentId));
    }
}
