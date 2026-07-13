package com.hospital.management.mapper;

import com.hospital.management.dto.AppointmentDto;
import com.hospital.management.dto.DoctorDto;
import com.hospital.management.dto.PatientDto;
import com.hospital.management.entity.Appointment;
import com.hospital.management.entity.AppointmentStatus;
import com.hospital.management.entity.QueueStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import javax.annotation.processing.Generated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-11T23:45:39+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 23.0.1 (Oracle Corporation)"
)
@Component
public class AppointmentMapperImpl implements AppointmentMapper {

    @Autowired
    private PatientMapper patientMapper;
    @Autowired
    private DoctorMapper doctorMapper;

    @Override
    public AppointmentDto toDto(Appointment appointment) {
        if ( appointment == null ) {
            return null;
        }

        Long id = null;
        PatientDto patient = null;
        DoctorDto doctor = null;
        LocalDate appointmentDate = null;
        LocalTime timeSlot = null;
        AppointmentStatus status = null;
        int tokenNumber = 0;
        QueueStatus queueStatus = null;

        id = appointment.getId();
        patient = patientMapper.toDto( appointment.getPatient() );
        doctor = doctorMapper.toDto( appointment.getDoctor() );
        appointmentDate = appointment.getAppointmentDate();
        timeSlot = appointment.getTimeSlot();
        status = appointment.getStatus();
        tokenNumber = appointment.getTokenNumber();
        queueStatus = appointment.getQueueStatus();

        AppointmentDto appointmentDto = new AppointmentDto( id, patient, doctor, appointmentDate, timeSlot, status, tokenNumber, queueStatus );

        return appointmentDto;
    }
}
