package com.hospital.management.service;

import com.hospital.management.dto.DoctorDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface DoctorService {
    DoctorDto getDoctorById(Long id);
    Page<DoctorDto> searchDoctors(String query, Pageable pageable);
    List<DoctorDto> findDoctorsByDepartment(Long departmentId);
}
