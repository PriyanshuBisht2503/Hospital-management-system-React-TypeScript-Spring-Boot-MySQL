package com.hospital.management.serviceImpl;

import com.hospital.management.dto.auth.*;
import com.hospital.management.entity.Permission;
import com.hospital.management.entity.RefreshToken;
import com.hospital.management.entity.Role;
import com.hospital.management.entity.User;
import com.hospital.management.exception.DuplicateResourceException;
import com.hospital.management.exception.ResourceNotFoundException;
import com.hospital.management.exception.UnauthorizedException;
import com.hospital.management.mapper.UserMapper;
import com.hospital.management.repository.RoleRepository;
import com.hospital.management.repository.RefreshTokenRepository;
import com.hospital.management.repository.UserRepository;
import com.hospital.management.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        log.info("Processing login request for user: {}", request.email());
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> {
                log.warn("Login failure: user with email {} not found", request.email());
                return new UnauthorizedException("Invalid email or password");
            });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failure: password mismatch for user {}", request.email());
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!user.isActive()) {
            log.warn("Login failure: account is inactive for user {}", request.email());
            throw new UnauthorizedException("User account is locked or disabled");
        }

        log.info("User {} successfully authenticated", request.email());

        Set<String> roles = user.getRoles().stream()
            .map(Role::getName)
            .collect(Collectors.toSet());

        Set<String> permissions = user.getRoles().stream()
            .flatMap(role -> role.getPermissions().stream())
            .map(Permission::getName)
            .collect(Collectors.toSet());

        // Standard token stubbing (real signature logic handled in Security layer)
        String mockAccessToken = "mock-jwt-access-token-" + UUID.randomUUID();
        String mockRefreshToken = UUID.randomUUID().toString();

        // Invalidate old tokens and persist new refresh token
        refreshTokenRepository.revokeAllUserTokens(user.getId());
        
        RefreshToken refreshTokenEntity = RefreshToken.builder()
            .user(user)
            .token(mockRefreshToken)
            .hashedToken(passwordEncoder.encode(mockRefreshToken))
            .revoked(false)
            .expiresAt(LocalDateTime.now().plusDays(7))
            .build();
        refreshTokenRepository.save(refreshTokenEntity);

        return new LoginResponse(
            mockAccessToken,
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            roles,
            permissions
        );
    }

    @Override
    public TokenRefreshResponse refresh(TokenRefreshRequest request) {
        RefreshToken tokenEntity = refreshTokenRepository.findByToken(request.refreshToken())
            .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (tokenEntity.isRevoked() || tokenEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Revoked or expired refresh token");
        }

        User user = tokenEntity.getUser();
        String newAccessToken = "mock-jwt-access-token-" + UUID.randomUUID();
        String newRefreshToken = UUID.randomUUID().toString();

        // Rotate token
        tokenEntity.setRevoked(true);
        refreshTokenRepository.save(tokenEntity);

        RefreshToken newRefreshTokenEntity = RefreshToken.builder()
            .user(user)
            .token(newRefreshToken)
            .hashedToken(passwordEncoder.encode(newRefreshToken))
            .revoked(false)
            .expiresAt(LocalDateTime.now().plusDays(7))
            .build();
        refreshTokenRepository.save(newRefreshTokenEntity);

        return new TokenRefreshResponse(newAccessToken, newRefreshToken);
    }

    @Override
    public void logout(String token) {
        log.info("Processing logout for refresh token");
        Optional<RefreshToken> tokenEntityOpt = refreshTokenRepository.findByToken(token);
        if (tokenEntityOpt.isPresent()) {
            RefreshToken tokenEntity = tokenEntityOpt.get();
            tokenEntity.setRevoked(true);
            refreshTokenRepository.save(tokenEntity);
            log.info("Refresh token successfully revoked");
        }
    }

    @Override
    public void register(RegisterRequest request) {
        log.info("Processing registration request for user: {}", request.email());
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration failed: email {} already exists", request.email());
            throw new DuplicateResourceException("Email is already registered");
        }

        Role defaultRole = roleRepository.findByName("ROLE_PATIENT")
            .orElseThrow(() -> {
                log.error("Critical: Default patient role not configured in system database");
                return new ResourceNotFoundException("Default patient role not configured");
            });

        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRoles(new HashSet<>(Set.of(defaultRole)));

        userRepository.save(user);
        log.info("User {} successfully registered as a PATIENT", request.email());
    }
}
