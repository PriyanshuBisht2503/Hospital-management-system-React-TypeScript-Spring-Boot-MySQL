package com.hospital.management.mapper;

import com.hospital.management.dto.PatientDto;
import com.hospital.management.entity.Patient;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface PatientMapper {
    PatientDto toDto(Patient patient);
}
