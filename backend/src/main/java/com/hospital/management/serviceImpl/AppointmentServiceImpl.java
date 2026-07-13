package com.hospital.management.serviceImpl;

import com.hospital.management.dto.AppointmentBookRequest;
import com.hospital.management.dto.AppointmentDto;
import com.hospital.management.entity.*;
import com.hospital.management.exception.AppointmentConflictException;
import com.hospital.management.exception.ResourceNotFoundException;
import com.hospital.management.event.AppointmentBookedEvent;
import com.hospital.management.mapper.AppointmentMapper;
import com.hospital.management.repository.AppointmentRepository;
import com.hospital.management.repository.DoctorRepository;
import com.hospital.management.repository.PatientRepository;
import com.hospital.management.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentMapper appointmentMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public AppointmentDto bookAppointment(AppointmentBookRequest request) {
        log.info("Request received to book appointment for Patient ID: {} with Doctor ID: {} on {}", 
            request.patientId(), request.doctorId(), request.appointmentDate());

        // Concurrency Guard: lock Doctor row before check/book to serialize concurrent checks
        Doctor doctor = doctorRepository.findByIdForUpdate(request.doctorId())
            .orElseThrow(() -> {
                log.warn("Booking failed: doctor ID {} not found", request.doctorId());
                return new ResourceNotFoundException("Doctor not found with id: " + request.doctorId());
            });

        Patient patient = patientRepository.findById(request.patientId())
            .orElseThrow(() -> {
                log.warn("Booking failed: patient ID {} not found", request.patientId());
                return new ResourceNotFoundException("Patient not found with id: " + request.patientId());
            });

        // Concurrency Guard: Check slot availability with overlap checks
        List<Appointment> overlapping = appointmentRepository.findOverlappingAppointments(
            doctor.getId(),
            request.appointmentDate(),
            request.timeSlot()
        );

        if (!overlapping.isEmpty()) {
            log.warn("Booking failed: slot is already occupied for Doctor ID: {} at {}", doctor.getId(), request.timeSlot());
            throw new AppointmentConflictException("The selected slot is already booked for this doctor");
        }

        // Generate Token Number for the day
        long tokenCount = appointmentRepository.countConfirmedAppointments(doctor.getId(), request.appointmentDate());
        int tokenNumber = (int) (tokenCount + 1);

        Appointment appointment = Appointment.builder()
            .patient(patient)
            .doctor(doctor)
            .appointmentDate(request.appointmentDate())
            .timeSlot(request.timeSlot())
            .status(AppointmentStatus.CONFIRMED)
            .tokenNumber(tokenNumber)
            .queueStatus(QueueStatus.WAITING)
            .build();

        appointment = appointmentRepository.save(appointment);
        log.info("Appointment successfully booked with ID: {}, Token Number: {}", appointment.getId(), tokenNumber);

        // Publish Domain Event
        eventPublisher.publishEvent(new AppointmentBookedEvent(
            appointment.getId(),
            patient.getId(),
            doctor.getId(),
            appointment.getAppointmentDate(),
            appointment.getTimeSlot(),
            appointment.getTokenNumber()
        ));

        return appointmentMapper.toDto(appointment);
    }

    @Override
    public void cancelAppointment(Long id) {
        log.info("Processing cancellation request for Appointment ID: {}", id);
        // Concurrency Guard: lock appointment record for cancellation transaction
        Appointment appointment = appointmentRepository.findByIdForUpdate(id)
            .orElseThrow(() -> {
                log.warn("Cancellation failed: Appointment ID {} not found", id);
                return new ResourceNotFoundException("Appointment not found with id: " + id);
            });

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            log.info("Appointment ID {} is already cancelled", id);
            return;
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
        log.info("Appointment ID {} successfully cancelled", id);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentDto getAppointmentById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + id));
        return appointmentMapper.toDto(appointment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentDto> getPatientAppointments(Long patientId, Pageable pageable) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found with id: " + patientId);
        }
        return appointmentRepository.findByPatientId(patientId, pageable).map(appointmentMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentDto> getDoctorAppointments(Long doctorId, Pageable pageable) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Doctor not found with id: " + doctorId);
        }
        return appointmentRepository.findByDoctorId(doctorId, pageable).map(appointmentMapper::toDto);
    }
}
