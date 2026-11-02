package com.hms.api.service;

import com.hms.api.domain.Patient;
import com.hms.api.domain.Role;
import com.hms.api.dto.CreatePatientRequest;
import com.hms.api.dto.PageResponse;
import com.hms.api.dto.PatientDto;
import com.hms.api.exception.BadRequestException;
import com.hms.api.exception.ResourceNotFoundException;
import com.hms.api.repository.AppointmentRepository;
import com.hms.api.repository.PatientRepository;
import com.hms.api.security.SecurityUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class PatientService {

    public static final int DEFAULT_PAGE_SIZE = 6;
    public static final int MAX_PAGE_SIZE = 100;

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public PatientService(
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            AuditLogService auditLogService,
            Clock clock
    ) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PatientDto getPatientById(Long id, SecurityUser currentUser, String clientIp) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + id));

        // Patient role can only view their own record; return 404 to prevent enumeration
        if (currentUser.getRole() == Role.PATIENT) {
            if (patient.getUser() == null || !patient.getUser().getId().equals(currentUser.getId())) {
                throw new ResourceNotFoundException("Patient not found: " + id);
            }
        }

        // Doctor role can only view patients who have or had an appointment with them
        if (currentUser.getRole() == Role.DOCTOR) {
            boolean hasAppointment = appointmentRepository.existsByDoctorUserIdAndPatientId(currentUser.getId(), id);
            if (!hasAppointment) {
                throw new ResourceNotFoundException("Patient not found: " + id);
            }
        }

        // Rule 9: Audited with VIEW_PATIENT, entity id only, no personal data in details
        auditLogService.log(currentUser.getId(), "VIEW_PATIENT", "PATIENT", String.valueOf(id), clientIp, null);

        boolean maskContact = currentUser.getRole() == Role.DOCTOR;
        return PatientDto.fromEntity(patient, maskContact, clock);
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientDto> searchPatients(String query, int page, int size, SecurityUser currentUser, String clientIp) {
        if (currentUser.getRole() == Role.PATIENT) {
            throw new AccessDeniedException("Access denied: patients cannot search the patient directory");
        }

        int pageSize = (size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int pageNumber = Math.max(0, page);

        Page<Patient> result;
        if (currentUser.getRole() == Role.DOCTOR) {
            result = patientRepository.searchPatientsByDoctorUserId(
                    currentUser.getId(),
                    query,
                    PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
            );
        } else {
            result = patientRepository.searchPatients(
                    query,
                    PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
            );
        }

        // Rule 9: Audited with SEARCH_PATIENTS, search target only, no personal data in details
        String auditTarget = (query != null && !query.isBlank()) ? query.trim() : "ALL";
        auditLogService.log(currentUser.getId(), "SEARCH_PATIENTS", "PATIENT", auditTarget, clientIp, null);

        boolean maskContact = currentUser.getRole() == Role.DOCTOR;
        Page<PatientDto> dtoPage = result.map(p -> PatientDto.fromEntity(p, maskContact, clock));
        return PageResponse.from(dtoPage);
    }

    @Transactional
    public PatientDto createPatient(CreatePatientRequest request, SecurityUser currentUser, String clientIp) {
        if (!request.hasContact()) {
            throw new BadRequestException("At least one contact method (phone or email) is required");
        }

        String phone = request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null;
        String email = request.email() != null && !request.email().isBlank() ? request.email().trim().toLowerCase() : null;

        Patient patient = new Patient(request.fullName().trim(), phone, email, request.dateOfBirth(), request.gender());
        Patient saved = patientRepository.save(patient);

        auditLogService.log(currentUser.getId(), "CREATE_PATIENT", "PATIENT", String.valueOf(saved.getId()), clientIp, null);

        boolean maskContact = currentUser.getRole() == Role.DOCTOR;
        return PatientDto.fromEntity(saved, maskContact, clock);
    }
}

