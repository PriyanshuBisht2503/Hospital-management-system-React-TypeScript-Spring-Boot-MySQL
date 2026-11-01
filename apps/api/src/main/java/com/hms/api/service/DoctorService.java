package com.hms.api.service;

import com.hms.api.annotation.Audited;
import com.hms.api.domain.Branch;
import com.hms.api.domain.Doctor;
import com.hms.api.domain.Role;
import com.hms.api.domain.Specialty;
import com.hms.api.domain.User;
import com.hms.api.dto.BranchDto;
import com.hms.api.dto.DoctorAdminDto;
import com.hms.api.dto.DoctorCreateRequest;
import com.hms.api.dto.DoctorPublicDto;
import com.hms.api.dto.DoctorUpdateRequest;
import com.hms.api.dto.PageResponse;
import com.hms.api.dto.SpecialtyDto;
import com.hms.api.exception.BadRequestException;
import com.hms.api.exception.ConflictException;
import com.hms.api.exception.ResourceNotFoundException;
import com.hms.api.repository.BranchRepository;
import com.hms.api.repository.DoctorRepository;
import com.hms.api.repository.RefreshTokenRepository;
import com.hms.api.repository.SpecialtyRepository;
import com.hms.api.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final SpecialtyRepository specialtyRepository;
    private final BranchRepository branchRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public DoctorService(
            DoctorRepository doctorRepository,
            UserRepository userRepository,
            SpecialtyRepository specialtyRepository,
            BranchRepository branchRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.specialtyRepository = specialtyRepository;
        this.branchRepository = branchRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResponse<DoctorPublicDto> getPublicDoctors(Long specialtyId, Long branchId, String search, Pageable pageable) {
        Page<Doctor> page = doctorRepository.searchActiveDoctors(specialtyId, branchId, search, pageable);
        return PageResponse.from(page.map(this::toPublicDto));
    }

    public DoctorPublicDto getPublicDoctorById(Long id) {
        Doctor doctor = doctorRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Active doctor not found with id: " + id));
        return toPublicDto(doctor);
    }

    public PageResponse<DoctorAdminDto> getAdminDoctors(Long specialtyId, Long branchId, Boolean isActive, String search, Pageable pageable) {
        Page<Doctor> page = doctorRepository.searchDoctors(specialtyId, branchId, isActive, search, pageable);
        return PageResponse.from(page.map(this::toAdminDto));
    }

    public DoctorAdminDto getAdminDoctorById(Long id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id: " + id));
        return toAdminDto(doctor);
    }

    @Transactional
    @Audited(action = "CREATE_DOCTOR", entityType = "DOCTOR")
    public DoctorAdminDto createDoctor(DoctorCreateRequest req) {
        if (req.password() == null || req.password().length() < 8) {
            throw new BadRequestException("Initial password must be at least 8 characters long");
        }

        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("User with email '" + req.email() + "' already exists");
        }

        if (userRepository.existsByPhone(req.phone())) {
            throw new ConflictException("User with phone '" + req.phone() + "' already exists");
        }

        // 1. Create User
        User user = new User(
            req.email(),
            req.phone(),
            passwordEncoder.encode(req.password()),
            Role.DOCTOR,
            req.fullName()
        );
        user.setEnabled(true);
        User savedUser = userRepository.save(user);

        // 2. Resolve Specialties and Branches
        Set<Specialty> specialties = new HashSet<>();
        if (req.specialtyIds() != null && !req.specialtyIds().isEmpty()) {
            specialties.addAll(specialtyRepository.findAllById(req.specialtyIds()));
        }

        Set<Branch> branches = new HashSet<>();
        if (req.branchIds() != null && !req.branchIds().isEmpty()) {
            branches.addAll(branchRepository.findAllById(req.branchIds()));
        }

        // 3. Create Doctor Profile
        Doctor doctor = new Doctor(
            savedUser,
            req.bio(),
            req.qualifications(),
            req.languages(),
            req.experienceYears(),
            req.consultationFee()
        );
        doctor.setPhotoUrl(req.photoUrl());
        doctor.setSpecialties(specialties);
        doctor.setBranches(branches);
        doctor.setActive(true);

        Doctor savedDoctor = doctorRepository.save(doctor);
        return toAdminDto(savedDoctor);
    }

    @Transactional
    @Audited(action = "UPDATE_DOCTOR", entityType = "DOCTOR", entityIdParam = "id")
    public DoctorAdminDto updateDoctor(Long id, DoctorUpdateRequest req) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id: " + id));

        User user = doctor.getUser();
        if (req.fullName() != null && !req.fullName().isBlank()) {
            user.setFullName(req.fullName());
        }
        if (req.phone() != null && !req.phone().isBlank() && !req.phone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(req.phone())) {
                throw new ConflictException("User with phone '" + req.phone() + "' already exists");
            }
            user.setPhone(req.phone());
        }
        userRepository.save(user);

        if (req.bio() != null) doctor.setBio(req.bio());
        if (req.qualifications() != null) doctor.setQualifications(req.qualifications());
        if (req.languages() != null) doctor.setLanguages(req.languages());
        if (req.experienceYears() != null) doctor.setExperienceYears(req.experienceYears());
        if (req.photoUrl() != null) doctor.setPhotoUrl(req.photoUrl());
        if (req.consultationFee() != null) doctor.setConsultationFee(req.consultationFee());

        if (req.specialtyIds() != null) {
            Set<Specialty> specialties = new HashSet<>(specialtyRepository.findAllById(req.specialtyIds()));
            doctor.setSpecialties(specialties);
        }

        if (req.branchIds() != null) {
            Set<Branch> branches = new HashSet<>(branchRepository.findAllById(req.branchIds()));
            doctor.setBranches(branches);
        }

        Doctor saved = doctorRepository.save(doctor);
        return toAdminDto(saved);
    }

    @Transactional
    @Audited(action = "DEACTIVATE_DOCTOR", entityType = "DOCTOR", entityIdParam = "id")
    public void deactivateDoctor(Long id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id: " + id));

        // 1. Soft-delete doctor profile
        doctor.setActive(false);
        doctorRepository.saveAndFlush(doctor);

        // 2. Disable linked user
        if (doctor.getUser() != null) {
            User user = userRepository.findById(doctor.getUser().getId()).orElse(doctor.getUser());
            user.setEnabled(false);
            userRepository.saveAndFlush(user);

            // 3. Revoke all refresh tokens
            refreshTokenRepository.revokeAllByUserId(user.getId());
        }
    }

    private DoctorPublicDto toPublicDto(Doctor d) {
        Set<SpecialtyDto> specialties = d.getSpecialties().stream()
                .filter(Specialty::isActive)
                .map(SpecialtyDto::from)
                .collect(Collectors.toSet());

        Set<BranchDto> branches = d.getBranches().stream()
                .filter(Branch::isActive)
                .map(BranchDto::from)
                .collect(Collectors.toSet());

        return new DoctorPublicDto(
            d.getId(),
            d.getUser().getFullName(),
            d.getBio(),
            d.getQualifications(),
            d.getLanguages(),
            d.getExperienceYears(),
            d.getPhotoUrl(),
            d.getConsultationFee(),
            specialties,
            branches,
            d.isActive()
        );
    }

    private DoctorAdminDto toAdminDto(Doctor d) {
        Set<SpecialtyDto> specialties = d.getSpecialties().stream()
                .map(SpecialtyDto::from)
                .collect(Collectors.toSet());

        Set<BranchDto> branches = d.getBranches().stream()
                .map(BranchDto::from)
                .collect(Collectors.toSet());

        return new DoctorAdminDto(
            d.getId(),
            d.getUser().getId(),
            d.getUser().getEmail(),
            d.getUser().getPhone(),
            d.getUser().getFullName(),
            d.getBio(),
            d.getQualifications(),
            d.getLanguages(),
            d.getExperienceYears(),
            d.getPhotoUrl(),
            d.getConsultationFee(),
            specialties,
            branches,
            d.isActive()
        );
    }
}
