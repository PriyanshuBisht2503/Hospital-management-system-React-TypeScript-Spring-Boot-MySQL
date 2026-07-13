package com.hospital.management.repository;

import com.hospital.management.entity.Appointment;
import com.hospital.management.entity.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a WHERE a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user"})
    Page<Appointment> findByPatientId(Long patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user"})
    Page<Appointment> findByDoctorId(Long doctorId, Pageable pageable);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id = :doctorId AND a.appointmentDate = :date AND a.status = 'CONFIRMED'")
    long countConfirmedAppointments(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND a.appointmentDate = :date AND a.timeSlot = :slot AND a.status != 'CANCELLED'")
    List<Appointment> findOverlappingAppointments(@Param("doctorId") Long doctorId, @Param("date") LocalDate date, @Param("slot") LocalTime slot);
}
