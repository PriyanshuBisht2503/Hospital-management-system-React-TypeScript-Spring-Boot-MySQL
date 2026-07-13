package com.hospital.management.serviceImpl;

import com.hospital.management.dto.DoctorDto;
import com.hospital.management.entity.Doctor;
import com.hospital.management.exception.ResourceNotFoundException;
import com.hospital.management.mapper.DoctorMapper;
import com.hospital.management.repository.DoctorRepository;
import com.hospital.management.service.DoctorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DoctorServiceImpl implements DoctorService {

    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;

    @Override
    public DoctorDto getDoctorById(Long id) {
        log.info("Fetching doctor record for Doctor ID: {}", id);
        Doctor doctor = doctorRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("Doctor lookup failed: Doctor ID {} not found", id);
                return new ResourceNotFoundException("Doctor not found with id: " + id);
            });
        return doctorMapper.toDto(doctor);
    }

    @Override
    public Page<DoctorDto> searchDoctors(String query, Pageable pageable) {
        Page<Doctor> doctorPage;
        if (query == null || query.isBlank()) {
            doctorPage = doctorRepository.findAll(pageable);
        } else {
            doctorPage = doctorRepository.searchDoctors(query, pageable);
        }
        return doctorPage.map(doctorMapper::toDto);
    }

    @Override
    public List<DoctorDto> findDoctorsByDepartment(Long departmentId) {
        return doctorRepository.findByDepartmentId(departmentId).stream()
            .map(doctorMapper::toDto)
            .collect(Collectors.toList());
    }
}
