package com.hms.api.web;

import com.hms.api.dto.MessageResponse;
import com.hms.api.dto.PageResponse;
import com.hms.api.dto.SpecialtyDto;
import com.hms.api.dto.SpecialtyRequest;
import com.hms.api.security.SecurityUser;
import com.hms.api.service.SpecialtyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/specialties")
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    public SpecialtyController(SpecialtyService specialtyService) {
        this.specialtyService = specialtyService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<SpecialtyDto>> listSpecialties(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.isAuthenticated() &&
                auth.getPrincipal() instanceof SecurityUser u &&
                u.getRole() == com.hms.api.domain.Role.ADMIN;

        Boolean effectiveActive = isAdmin ? active : Boolean.TRUE;
        return ResponseEntity.ok(specialtyService.getSpecialties(search, effectiveActive, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpecialtyDto> getSpecialtyById(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.isAuthenticated() &&
                auth.getPrincipal() instanceof SecurityUser u &&
                u.getRole() == com.hms.api.domain.Role.ADMIN;

        return ResponseEntity.ok(specialtyService.getSpecialtyById(id, !isAdmin));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpecialtyDto> createSpecialty(@Valid @RequestBody SpecialtyRequest req) {
        SpecialtyDto created = specialtyService.createSpecialty(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpecialtyDto> updateSpecialty(
            @PathVariable Long id,
            @Valid @RequestBody SpecialtyRequest req
    ) {
        return ResponseEntity.ok(specialtyService.updateSpecialty(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deactivateSpecialty(@PathVariable Long id) {
        specialtyService.deactivateSpecialty(id);
        return ResponseEntity.ok(new MessageResponse("Specialty deactivated successfully"));
    }
}
