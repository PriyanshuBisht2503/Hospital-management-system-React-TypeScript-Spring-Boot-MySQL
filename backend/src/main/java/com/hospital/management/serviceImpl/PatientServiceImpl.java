package com.hospital.management.serviceImpl;

import com.hospital.management.dto.PatientDto;
import com.hospital.management.dto.PatientRegistrationDto;
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
import com.hospital.management.service.PatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PatientMapper patientMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PatientDto registerPatient(PatientRegistrationDto registrationDto) {
        log.info("Processing patient registration request: {}", registrationDto.user().email());
        if (userRepository.existsByEmail(registrationDto.user().email())) {
            log.warn("Patient registration failed: email {} is duplicate", registrationDto.user().email());
            throw new DuplicateResourceException("User email is already registered");
        }

        Role patientRole = roleRepository.findByName("ROLE_PATIENT")
            .orElseThrow(() -> {
                log.error("Critical: Patient role not found in system database");
                return new ResourceNotFoundException("Patient role not configured");
            });

        User user = User.builder()
            .email(registrationDto.user().email())
            .passwordHash(passwordEncoder.encode(registrationDto.user().password()))
            .firstName(registrationDto.user().firstName())
            .lastName(registrationDto.user().lastName())
            .phone(registrationDto.user().phone())
            .active(true)
            .roles(new HashSet<>(Set.of(patientRole)))
            .build();

        user = userRepository.save(user);

        Patient patient = Patient.builder()
            .user(user)
            .dateOfBirth(registrationDto.dateOfBirth())
            .gender(registrationDto.gender())
            .bloodGroup(registrationDto.bloodGroup())
            .address(registrationDto.address())
            .emergencyContactName(registrationDto.emergencyContactName())
            .emergencyContactPhone(registrationDto.emergencyContactPhone())
            .build();

        patient = patientRepository.save(patient);
        log.info("Patient profile successfully created with ID: {}", patient.getId());

        // Publish Domain Event
        eventPublisher.publishEvent(new PatientRegisteredEvent(
            patient.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName()
        ));

        return patientMapper.toDto(patient);
    }

    @Override
    @Transactional(readOnly = true)
    public PatientDto getPatientById(Long id) {
        Patient patient = patientRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Patient not found with id: " + id));
        return patientMapper.toDto(patient);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PatientDto> searchPatients(String query, Pageable pageable) {
        Page<Patient> patientPage;
        if (query == null || query.isBlank()) {
            patientPage = patientRepository.findAll(pageable);
        } else {
            patientPage = patientRepository.searchPatients(query, pageable);
        }
        return patientPage.map(patientMapper::toDto);
    }

    @Override
    public void deletePatient(Long id) {
        Patient patient = patientRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Patient not found with id: " + id));
        patientRepository.delete(patient); // Automatically triggers soft delete in JPA
    }
}
