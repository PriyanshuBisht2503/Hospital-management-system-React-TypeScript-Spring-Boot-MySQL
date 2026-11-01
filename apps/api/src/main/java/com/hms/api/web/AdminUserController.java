package com.hms.api.web;

import com.hms.api.domain.Role;
import com.hms.api.dto.ChangeRoleRequest;
import com.hms.api.dto.CreateStaffRequest;
import com.hms.api.dto.PageResponse;
import com.hms.api.dto.UpdateUserStatusRequest;
import com.hms.api.dto.UserDto;
import com.hms.api.security.SecurityUser;
import com.hms.api.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<UserDto>> listUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(userService.searchUsers(role, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping
    public ResponseEntity<UserDto> createStaff(@Valid @RequestBody CreateStaffRequest req) {
        UserDto created = userService.createStaff(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<UserDto> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest req,
            @AuthenticationPrincipal SecurityUser currentUser
    ) {
        Long currentAdminId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(userService.changeRole(id, req, currentAdminId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UserDto> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest req,
            @AuthenticationPrincipal SecurityUser currentUser
    ) {
        Long currentAdminId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(userService.updateUserStatus(id, req, currentAdminId));
    }
}
