package com.hospital.management.service;

import com.hospital.management.dto.PatientDto;
import com.hospital.management.dto.PatientRegistrationDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PatientService {
    PatientDto registerPatient(PatientRegistrationDto registrationDto);
    PatientDto getPatientById(Long id);
    Page<PatientDto> searchPatients(String query, Pageable pageable);
    void deletePatient(Long id);
}
