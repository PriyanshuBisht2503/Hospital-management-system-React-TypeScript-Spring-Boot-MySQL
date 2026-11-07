package com.hms.api.web;

import com.hms.api.dto.BranchDto;
import com.hms.api.dto.BranchRequest;
import com.hms.api.dto.MessageResponse;
import com.hms.api.dto.PageResponse;
import com.hms.api.security.SecurityUser;
import com.hms.api.service.BranchService;
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
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<BranchDto>> listBranches(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.isAuthenticated() &&
                auth.getPrincipal() instanceof SecurityUser u &&
                u.getRole() == com.hms.api.domain.Role.ADMIN;

        // Public/unauthenticated callers always get only active branches
        Boolean effectiveActive = isAdmin ? active : Boolean.TRUE;
        return ResponseEntity.ok(branchService.getBranches(search, effectiveActive, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BranchDto> getBranchById(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.isAuthenticated() &&
                auth.getPrincipal() instanceof SecurityUser u &&
                u.getRole() == com.hms.api.domain.Role.ADMIN;

        return ResponseEntity.ok(branchService.getBranchById(id, !isAdmin));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BranchDto> createBranch(@Valid @RequestBody BranchRequest req) {
        BranchDto created = branchService.createBranch(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BranchDto> updateBranch(
            @PathVariable Long id,
            @Valid @RequestBody BranchRequest req
    ) {
        return ResponseEntity.ok(branchService.updateBranch(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deactivateBranch(@PathVariable Long id) {
        branchService.deactivateBranch(id);
        return ResponseEntity.ok(new MessageResponse("Branch deactivated successfully"));
    }
}

