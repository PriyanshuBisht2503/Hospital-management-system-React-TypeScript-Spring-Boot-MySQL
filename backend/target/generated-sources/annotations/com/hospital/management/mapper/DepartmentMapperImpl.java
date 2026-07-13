package com.hospital.management.mapper;

import com.hospital.management.dto.DepartmentDto;
import com.hospital.management.entity.Department;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-11T23:45:39+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 23.0.1 (Oracle Corporation)"
)
@Component
public class DepartmentMapperImpl implements DepartmentMapper {

    @Override
    public DepartmentDto toDto(Department department) {
        if ( department == null ) {
            return null;
        }

        Long id = null;
        String name = null;
        String code = null;
        String description = null;

        id = department.getId();
        name = department.getName();
        code = department.getCode();
        description = department.getDescription();

        DepartmentDto departmentDto = new DepartmentDto( id, name, code, description );

        return departmentDto;
    }

    @Override
    public Department toEntity(DepartmentDto dto) {
        if ( dto == null ) {
            return null;
        }

        Department.DepartmentBuilder department = Department.builder();

        department.id( dto.id() );
        department.name( dto.name() );
        department.code( dto.code() );
        department.description( dto.description() );

        return department.build();
    }
}
