package com.hms.api.web;

import com.hms.api.dto.DepartmentDto;
import com.hms.api.dto.DepartmentRequest;
import com.hms.api.dto.MessageResponse;
import com.hms.api.dto.PageResponse;
import com.hms.api.security.SecurityUser;
import com.hms.api.service.DepartmentService;
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
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<DepartmentDto>> listDepartments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.isAuthenticated() &&
                auth.getPrincipal() instanceof SecurityUser u &&
                u.getRole() == com.hms.api.domain.Role.ADMIN;

        Boolean effectiveActive = isAdmin ? active : Boolean.TRUE;
        return ResponseEntity.ok(departmentService.getDepartments(search, effectiveActive, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentDto> getDepartmentById(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.isAuthenticated() &&
                auth.getPrincipal() instanceof SecurityUser u &&
                u.getRole() == com.hms.api.domain.Role.ADMIN;

        return ResponseEntity.ok(departmentService.getDepartmentById(id, !isAdmin));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentDto> createDepartment(@Valid @RequestBody DepartmentRequest req) {
        DepartmentDto created = departmentService.createDepartment(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentDto> updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest req
    ) {
        return ResponseEntity.ok(departmentService.updateDepartment(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deactivateDepartment(@PathVariable Long id) {
        departmentService.deactivateDepartment(id);
        return ResponseEntity.ok(new MessageResponse("Department deactivated successfully"));
    }
}
