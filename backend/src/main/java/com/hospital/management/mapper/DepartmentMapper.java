package com.hospital.management.mapper;

import com.hospital.management.dto.DepartmentDto;
import com.hospital.management.entity.Department;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DepartmentMapper {
    DepartmentDto toDto(Department department);
    Department toEntity(DepartmentDto dto);
}
