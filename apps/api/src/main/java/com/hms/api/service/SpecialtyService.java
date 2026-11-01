package com.hms.api.service;

import com.hms.api.annotation.Audited;
import com.hms.api.domain.Specialty;
import com.hms.api.dto.PageResponse;
import com.hms.api.dto.SpecialtyDto;
import com.hms.api.dto.SpecialtyRequest;
import com.hms.api.exception.ConflictException;
import com.hms.api.exception.ResourceNotFoundException;
import com.hms.api.repository.DoctorRepository;
import com.hms.api.repository.SpecialtyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpecialtyService {

    private final SpecialtyRepository specialtyRepository;
    private final DoctorRepository doctorRepository;

    public SpecialtyService(
            SpecialtyRepository specialtyRepository,
            DoctorRepository doctorRepository
    ) {
        this.specialtyRepository = specialtyRepository;
        this.doctorRepository = doctorRepository;
    }

    public PageResponse<SpecialtyDto> getSpecialties(String search, Boolean isActive, Pageable pageable) {
        Page<Specialty> page = specialtyRepository.searchSpecialties(isActive, search, pageable);
        return PageResponse.from(page.map(SpecialtyDto::from));
    }

    public SpecialtyDto getSpecialtyById(Long id, boolean unauthenticated) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Specialty not found with id: " + id));

        if (unauthenticated && !specialty.isActive()) {
            throw new ResourceNotFoundException("Specialty not found with id: " + id);
        }

        return SpecialtyDto.from(specialty);
    }

    @Transactional
    @Audited(action = "CREATE_SPECIALTY", entityType = "SPECIALTY")
    public SpecialtyDto createSpecialty(SpecialtyRequest req) {
        if (specialtyRepository.existsByCode(req.code())) {
            throw new ConflictException("Specialty with code '" + req.code() + "' already exists");
        }

        Specialty specialty = new Specialty(req.name(), req.code(), req.description());
        Specialty saved = specialtyRepository.save(specialty);
        return SpecialtyDto.from(saved);
    }

    @Transactional
    @Audited(action = "UPDATE_SPECIALTY", entityType = "SPECIALTY", entityIdParam = "id")
    public SpecialtyDto updateSpecialty(Long id, SpecialtyRequest req) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Specialty not found with id: " + id));

        if (!specialty.getCode().equalsIgnoreCase(req.code()) && specialtyRepository.existsByCode(req.code())) {
            throw new ConflictException("Specialty with code '" + req.code() + "' already exists");
        }

        specialty.setName(req.name());
        specialty.setCode(req.code());
        specialty.setDescription(req.description());

        Specialty saved = specialtyRepository.save(specialty);
        return SpecialtyDto.from(saved);
    }

    @Transactional
    @Audited(action = "DEACTIVATE_SPECIALTY", entityType = "SPECIALTY", entityIdParam = "id")
    public void deactivateSpecialty(Long id) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Specialty not found with id: " + id));

        if (doctorRepository.existsBySpecialties_IdAndIsActiveTrue(id)) {
            throw new ConflictException("Cannot deactivate specialty: referenced by active doctors");
        }

        specialty.setActive(false);
        specialtyRepository.save(specialty);
    }
}
