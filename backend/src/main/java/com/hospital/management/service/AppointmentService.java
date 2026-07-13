package com.hospital.management.service;

import com.hospital.management.dto.AppointmentBookRequest;
import com.hospital.management.dto.AppointmentDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AppointmentService {
    AppointmentDto bookAppointment(AppointmentBookRequest request);
    void cancelAppointment(Long id);
    AppointmentDto getAppointmentById(Long id);
    Page<AppointmentDto> getPatientAppointments(Long patientId, Pageable pageable);
    Page<AppointmentDto> getDoctorAppointments(Long doctorId, Pageable pageable);
}
