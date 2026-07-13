package com.hospital.management.service;

import com.hospital.management.dto.PatientDto;
import com.hospital.management.dto.PatientRegistrationDto;
import com.hospital.management.dto.UserRegistrationDto;
import com.hospital.management.entity.Gender;
import com.hospital.management.entity.Patient;
import com.hospital.management.entity.Role;
import com.hospital.management.entity.User;
import com.hospital.management.exception.DuplicateResourceException;
import com.hospital.management.exception.ResourceNotFoundException;
import com.hospital.management.event.PatientRegisteredEvent;
import com.hospital.management.mapper.PatientMapper;
import com.hospital.management.repository.PatientRepository;
import com.hospital.management.repository.RoleRepository;
import com.hospital.management.repository.UserRepository;
import com.hospital.management.serviceImpl.PatientServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PatientMapper patientMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PatientServiceImpl patientService;

    private Patient testPatient;
    private User testUser;
    private Role patientRole;

    @BeforeEach
    public void setup() {
        patientRole = Role.builder().id(1L).name("ROLE_PATIENT").build();
        testUser = User.builder().id(1L).email("patient@example.com").passwordHash("hash").build();
        testPatient = Patient.builder()
            .id(1L)
            .user(testUser)
            .dateOfBirth(LocalDate.of(1990, 5, 10))
            .gender(Gender.MALE)
            .bloodGroup("A+")
            .build();
    }

    @Test
    public void testGetPatientById_Success() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(patientMapper.toDto(testPatient)).thenReturn(new PatientDto(1L, null, LocalDate.of(1990, 5, 10), Gender.MALE, "A+", "Address", "Contact", "1234"));

        PatientDto result = patientService.getPatientById(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
        verify(patientRepository, times(1)).findById(1L);
    }

    @Test
    public void testGetPatientById_Failure_NotFound() {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> patientService.getPatientById(99L));
    }

    @Test
    public void testRegisterPatient_Success() {
        UserRegistrationDto userDto = new UserRegistrationDto("new_patient@example.com", "pass", "John", "Doe", "12345");
        PatientRegistrationDto regDto = new PatientRegistrationDto(userDto, LocalDate.of(1995, 1, 1), Gender.FEMALE, "O+", "Addr", "Emerg", "Phone");

        when(userRepository.existsByEmail("new_patient@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_PATIENT")).thenReturn(Optional.of(patientRole));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(patientRepository.save(any(Patient.class))).thenReturn(testPatient);
        when(patientMapper.toDto(any(Patient.class))).thenReturn(new PatientDto(1L, null, LocalDate.of(1990, 5, 10), Gender.MALE, "A+", "Address", "Contact", "1234"));

        PatientDto result = patientService.registerPatient(regDto);

        assertNotNull(result);
        verify(userRepository, times(1)).save(any(User.class));
        verify(patientRepository, times(1)).save(any(Patient.class));
        verify(eventPublisher, times(1)).publishEvent(any(PatientRegisteredEvent.class));
    }

    @Test
    public void testRegisterPatient_Failure_DuplicateEmail() {
        UserRegistrationDto userDto = new UserRegistrationDto("patient@example.com", "pass", "John", "Doe", "12345");
        PatientRegistrationDto regDto = new PatientRegistrationDto(userDto, LocalDate.of(1995, 1, 1), Gender.FEMALE, "O+", "Addr", "Emerg", "Phone");

        when(userRepository.existsByEmail("patient@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> patientService.registerPatient(regDto));
        verify(patientRepository, never()).save(any(Patient.class));
    }
}
