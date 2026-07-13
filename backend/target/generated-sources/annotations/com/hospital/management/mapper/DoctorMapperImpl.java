package com.hospital.management.mapper;

import com.hospital.management.dto.DepartmentDto;
import com.hospital.management.dto.DoctorDto;
import com.hospital.management.dto.UserDto;
import com.hospital.management.entity.Doctor;
import java.math.BigDecimal;
import javax.annotation.processing.Generated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-11T23:45:40+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 23.0.1 (Oracle Corporation)"
)
@Component
public class DoctorMapperImpl implements DoctorMapper {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private DepartmentMapper departmentMapper;

    @Override
    public DoctorDto toDto(Doctor doctor) {
        if ( doctor == null ) {
            return null;
        }

        Long id = null;
        UserDto user = null;
        DepartmentDto department = null;
        String specialization = null;
        String licenseNumber = null;
        BigDecimal consultationFee = null;

        id = doctor.getId();
        user = userMapper.toDto( doctor.getUser() );
        department = departmentMapper.toDto( doctor.getDepartment() );
        specialization = doctor.getSpecialization();
        licenseNumber = doctor.getLicenseNumber();
        consultationFee = doctor.getConsultationFee();

        DoctorDto doctorDto = new DoctorDto( id, user, department, specialization, licenseNumber, consultationFee );

        return doctorDto;
    }
}
