package com.hms.api.service;

import com.hms.api.annotation.Audited;
import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import com.hms.api.dto.ChangeRoleRequest;
import com.hms.api.dto.CreateStaffRequest;
import com.hms.api.dto.PageResponse;
import com.hms.api.dto.UpdateUserStatusRequest;
import com.hms.api.dto.UserDto;
import com.hms.api.exception.BadRequestException;
import com.hms.api.exception.ConflictException;
import com.hms.api.exception.ResourceNotFoundException;
import com.hms.api.repository.DoctorRepository;
import com.hms.api.repository.RefreshTokenRepository;
import com.hms.api.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            DoctorRepository doctorRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResponse<UserDto> searchUsers(Role role, String search, Pageable pageable) {
        Page<User> page = userRepository.searchUsers(role, search, pageable);
        return PageResponse.from(page.map(UserDto::from));
    }

    public UserDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return UserDto.from(user);
    }

    @Transactional
    @Audited(action = "CREATE_STAFF", entityType = "USER")
    public UserDto createStaff(CreateStaffRequest req) {
        if (req.role() == Role.PATIENT) {
            throw new BadRequestException("Cannot create PATIENT via staff management");
        }

        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("User with email '" + req.email() + "' already exists");
        }

        if (userRepository.existsByPhone(req.phone())) {
            throw new ConflictException("User with phone '" + req.phone() + "' already exists");
        }

        User user = new User(
            req.email(),
            req.phone(),
            passwordEncoder.encode(req.password()),
            req.role(),
            req.fullName()
        );
        user.setEnabled(true);

        User saved = userRepository.save(user);
        return UserDto.from(saved);
    }

    @Transactional
    @Audited(action = "CHANGE_USER_ROLE", entityType = "USER", entityIdParam = "id")
    public UserDto changeRole(Long id, ChangeRoleRequest req, Long currentAdminUserId) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (req.role() == Role.PATIENT) {
            throw new BadRequestException("Cannot assign PATIENT role to staff");
        }

        // 1. Admin cannot demote themselves
        if (targetUser.getId().equals(currentAdminUserId)) {
            throw new ConflictException("An admin cannot change their own role");
        }

        // 2. Admin cannot demote last remaining ADMIN
        if (targetUser.getRole() == Role.ADMIN && req.role() != Role.ADMIN) {
            long activeAdminCount = userRepository.countByRoleAndEnabledTrue(Role.ADMIN);
            if (activeAdminCount <= 1) {
                throw new ConflictException("Cannot demote the last remaining active ADMIN");
            }
        }

        targetUser.setRole(req.role());
        User saved = userRepository.save(targetUser);

        // 3. Role change revokes all refresh tokens
        refreshTokenRepository.revokeAllByUserId(targetUser.getId());

        return UserDto.from(saved);
    }

    @Transactional
    @Audited(action = "UPDATE_USER_STATUS", entityType = "USER", entityIdParam = "id")
    public UserDto updateUserStatus(Long id, UpdateUserStatusRequest req, Long currentAdminUserId) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (!req.enabled()) {
            // 1. Admin cannot disable themselves
            if (targetUser.getId().equals(currentAdminUserId)) {
                throw new ConflictException("An admin cannot disable their own account");
            }

            // 2. Admin cannot disable last remaining active ADMIN
            if (targetUser.getRole() == Role.ADMIN) {
                long activeAdminCount = userRepository.countByRoleAndEnabledTrue(Role.ADMIN);
                if (activeAdminCount <= 1) {
                    throw new ConflictException("Cannot disable the last remaining active ADMIN");
                }
            }

            // 3. Disabling revokes all refresh tokens
            refreshTokenRepository.revokeAllByUserId(targetUser.getId());

            // 4. If linked to doctor profile, deactivate doctor profile
            doctorRepository.findByUserId(targetUser.getId()).ifPresent(doctor -> {
                doctor.setActive(false);
                doctorRepository.save(doctor);
            });
        }

        targetUser.setEnabled(req.enabled());
        User saved = userRepository.save(targetUser);
        return UserDto.from(saved);
    }
}

