package com.hospital.management.service;

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
import com.hospital.management.serviceImpl.AppointmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AppointmentMapper appointmentMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AppointmentServiceImpl appointmentService;

    private Doctor testDoctor;
    private Patient testPatient;
    private Appointment testAppointment;

    @BeforeEach
    public void setup() {
        testDoctor = Doctor.builder().id(1L).specialization("CARDIOLOGY").build();
        testPatient = Patient.builder().id(1L).build();
        testAppointment = Appointment.builder()
            .id(1L)
            .doctor(testDoctor)
            .patient(testPatient)
            .appointmentDate(LocalDate.of(2026, 8, 15))
            .timeSlot(LocalTime.of(9, 30))
            .status(AppointmentStatus.CONFIRMED)
            .tokenNumber(1)
            .build();
    }

    @Test
    public void testBookAppointment_Success() {
        AppointmentBookRequest request = new AppointmentBookRequest(1L, 1L, LocalDate.of(2026, 8, 15), LocalTime.of(9, 30));

        when(doctorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testDoctor));
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(appointmentRepository.findOverlappingAppointments(1L, LocalDate.of(2026, 8, 15), LocalTime.of(9, 30)))
            .thenReturn(new ArrayList<>());
        when(appointmentRepository.countConfirmedAppointments(1L, LocalDate.of(2026, 8, 15))).thenReturn(0L);
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(testAppointment);
        when(appointmentMapper.toDto(any(Appointment.class))).thenReturn(new AppointmentDto(1L, null, null, LocalDate.of(2026, 8, 15), LocalTime.of(9, 30), AppointmentStatus.CONFIRMED, 1, QueueStatus.WAITING));

        AppointmentDto result = appointmentService.bookAppointment(request);

        assertNotNull(result);
        assertEquals(1, result.tokenNumber());
        verify(eventPublisher, times(1)).publishEvent(any(AppointmentBookedEvent.class));
        verify(appointmentRepository, times(1)).save(any(Appointment.class));
    }

    @Test
    public void testBookAppointment_Failure_SlotOverlapped() {
        AppointmentBookRequest request = new AppointmentBookRequest(1L, 1L, LocalDate.of(2026, 8, 15), LocalTime.of(9, 30));

        when(doctorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testDoctor));
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(appointmentRepository.findOverlappingAppointments(1L, LocalDate.of(2026, 8, 15), LocalTime.of(9, 30)))
            .thenReturn(List.of(testAppointment)); // Overlap exists!

        assertThrows(AppointmentConflictException.class, () -> appointmentService.bookAppointment(request));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    public void testCancelAppointment_Success() {
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testAppointment));

        appointmentService.cancelAppointment(1L);

        assertEquals(AppointmentStatus.CANCELLED, testAppointment.getStatus());
        verify(appointmentRepository, times(1)).save(testAppointment);
    }
}
