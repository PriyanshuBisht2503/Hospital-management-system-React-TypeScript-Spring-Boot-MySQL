package com.hospital.management.mapper;

import com.hospital.management.dto.PatientDto;
import com.hospital.management.dto.UserDto;
import com.hospital.management.entity.Gender;
import com.hospital.management.entity.Patient;
import java.time.LocalDate;
import javax.annotation.processing.Generated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-11T23:45:39+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 23.0.1 (Oracle Corporation)"
)
@Component
public class PatientMapperImpl implements PatientMapper {

    @Autowired
    private UserMapper userMapper;

    @Override
    public PatientDto toDto(Patient patient) {
        if ( patient == null ) {
            return null;
        }

        Long id = null;
        UserDto user = null;
        LocalDate dateOfBirth = null;
        Gender gender = null;
        String bloodGroup = null;
        String address = null;
        String emergencyContactName = null;
        String emergencyContactPhone = null;

        id = patient.getId();
        user = userMapper.toDto( patient.getUser() );
        dateOfBirth = patient.getDateOfBirth();
        gender = patient.getGender();
        bloodGroup = patient.getBloodGroup();
        address = patient.getAddress();
        emergencyContactName = patient.getEmergencyContactName();
        emergencyContactPhone = patient.getEmergencyContactPhone();

        PatientDto patientDto = new PatientDto( id, user, dateOfBirth, gender, bloodGroup, address, emergencyContactName, emergencyContactPhone );

        return patientDto;
    }
}
