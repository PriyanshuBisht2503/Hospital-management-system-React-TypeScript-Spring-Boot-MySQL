package com.hospital.management.mapper;

import com.hospital.management.dto.DoctorDto;
import com.hospital.management.entity.Doctor;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {UserMapper.class, DepartmentMapper.class})
public interface DoctorMapper {
    DoctorDto toDto(Doctor doctor);
}
