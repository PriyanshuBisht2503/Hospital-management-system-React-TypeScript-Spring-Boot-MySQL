package com.hospital.management.service;

import com.hospital.management.dto.auth.*;
import com.hospital.management.entity.Permission;
import com.hospital.management.entity.Role;
import com.hospital.management.entity.User;
import com.hospital.management.exception.DuplicateResourceException;
import com.hospital.management.exception.UnauthorizedException;
import com.hospital.management.mapper.UserMapper;
import com.hospital.management.repository.RefreshTokenRepository;
import com.hospital.management.repository.RoleRepository;
import com.hospital.management.repository.UserRepository;
import com.hospital.management.serviceImpl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private Role testRole;

    @BeforeEach
    public void setup() {
        testRole = Role.builder().id(1L).name("ROLE_PATIENT").permissions(new HashSet<>()).build();
        testUser = User.builder()
            .id(1L)
            .email("test@example.com")
            .passwordHash("hashed_password")
            .active(true)
            .roles(new HashSet<>(Set.of(testRole)))
            .build();
    }

    @Test
    public void testLogin_Success() {
        LoginRequest request = new LoginRequest("test@example.com", "password");
        
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password", "hashed_password")).thenReturn(true);
        when(passwordEncoder.encode(any(String.class))).thenReturn("hashed_token");

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("test@example.com", response.email());
        assertTrue(response.roles().contains("ROLE_PATIENT"));
        verify(refreshTokenRepository, times(1)).revokeAllUserTokens(1L);
        verify(refreshTokenRepository, times(1)).save(any());
    }

    @Test
    public void testLogin_Failure_UserNotFound() {
        LoginRequest request = new LoginRequest("unknown@example.com", "password");
        
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.login(request));
    }

    @Test
    public void testLogin_Failure_PasswordMismatch() {
        LoginRequest request = new LoginRequest("test@example.com", "wrong_password");
        
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.login(request));
    }

    @Test
    public void testRegister_Success() {
        RegisterRequest request = new RegisterRequest("new@example.com", "password", "John", "Doe", "1234567890");
        
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_PATIENT")).thenReturn(Optional.of(testRole));
        when(userMapper.toEntity(request)).thenReturn(new User());

        authService.register(request);

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    public void testRegister_Failure_EmailExists() {
        RegisterRequest request = new RegisterRequest("test@example.com", "password", "John", "Doe", "1234567890");
        
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }
}
