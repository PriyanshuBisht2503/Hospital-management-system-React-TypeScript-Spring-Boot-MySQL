package com.hospital.management.mapper;

import com.hospital.management.dto.AppointmentDto;
import com.hospital.management.entity.Appointment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {PatientMapper.class, DoctorMapper.class})
public interface AppointmentMapper {
    AppointmentDto toDto(Appointment appointment);
}
