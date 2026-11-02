package com.hms.api.repository;

import com.hms.api.domain.Appointment;
import com.hms.api.domain.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("SELECT a.doctor.id FROM Appointment a WHERE a.id = :id")
    Optional<Long> findDoctorIdByAppointmentId(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user", "branch"})
    Optional<Appointment> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user", "branch"})
    @Query("SELECT a FROM Appointment a WHERE a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
           "AND a.status NOT IN ('CANCELLED', 'RESCHEDULED') " +
           "AND (:excludeId IS NULL OR a.id <> :excludeId) " +
           "AND a.startTime < :endTime AND a.endTime > :startTime")
    List<Appointment> findDoctorOverlappingAppointments(
            @Param("doctorId") Long doctorId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("excludeId") Long excludeId
    );

    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId " +
           "AND a.status NOT IN ('CANCELLED', 'RESCHEDULED') " +
           "AND (:excludeId IS NULL OR a.id <> :excludeId) " +
           "AND a.startTime < :endTime AND a.endTime > :startTime")
    List<Appointment> findPatientOverlappingAppointments(
            @Param("patientId") Long patientId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("excludeId") Long excludeId
    );

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
           "AND a.status NOT IN ('CANCELLED', 'RESCHEDULED') " +
           "AND a.startTime >= :from AND a.startTime < :to")
    List<Appointment> findActiveByDoctorInRange(
            @Param("doctorId") Long doctorId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id = :doctorId " +
           "AND a.status NOT IN ('CANCELLED', 'RESCHEDULED') " +
           "AND a.startTime >= :from")
    long countFutureActiveByDoctor(@Param("doctorId") Long doctorId, @Param("from") Instant from);

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
           "AND a.status NOT IN ('CANCELLED', 'RESCHEDULED') " +
           "AND a.startTime >= :from")
    List<Appointment> findFutureActiveByDoctor(@Param("doctorId") Long doctorId, @Param("from") Instant from);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.branch.id = :branchId " +
           "AND a.status NOT IN ('CANCELLED', 'RESCHEDULED') " +
           "AND a.startTime >= :from")
    long countFutureActiveByBranch(@Param("branchId") Long branchId, @Param("from") Instant from);

    @Query("SELECT a FROM Appointment a WHERE a.branch.id = :branchId " +
           "AND a.status NOT IN ('CANCELLED', 'RESCHEDULED') " +
           "AND a.startTime >= :from")
    List<Appointment> findFutureActiveByBranch(@Param("branchId") Long branchId, @Param("from") Instant from);

    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user", "branch"})
    @Query("SELECT a FROM Appointment a WHERE " +
           "(:doctorId IS NULL OR a.doctor.id = :doctorId) AND " +
           "(:branchId IS NULL OR a.branch.id = :branchId) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:patientId IS NULL OR a.patient.id = :patientId) AND " +
           "(:from IS NULL OR a.startTime >= :from) AND " +
           "(:to IS NULL OR a.startTime <= :to)")
    Page<Appointment> searchAppointments(
            @Param("doctorId") Long doctorId,
            @Param("branchId") Long branchId,
            @Param("status") AppointmentStatus status,
            @Param("patientId") Long patientId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user", "branch"})
    @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId " +
           "AND (:status IS NULL OR a.status = :status) " +
           "ORDER BY a.startTime DESC")
    Page<Appointment> findByPatientId(
            @Param("patientId") Long patientId,
            @Param("status") AppointmentStatus status,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Appointment a SET a.reminder24hSent = true WHERE a.id = :id AND a.reminder24hSent = false")
    int claimReminder24h(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Appointment a SET a.reminder2hSent = true WHERE a.id = :id AND a.reminder2hSent = false")
    int claimReminder2h(@Param("id") Long id);

    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user", "branch"})
    @Query("SELECT a FROM Appointment a WHERE a.status IN ('BOOKED', 'CONFIRMED') " +
           "AND a.reminder24hSent = false " +
           "AND a.startTime >= :start AND a.startTime <= :end")
    List<Appointment> findDueFor24hReminder(@Param("start") Instant start, @Param("end") Instant end);

    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user", "branch"})
    @Query("SELECT a FROM Appointment a WHERE a.status IN ('BOOKED', 'CONFIRMED') " +
           "AND a.reminder2hSent = false " +
           "AND a.startTime >= :start AND a.startTime <= :end")
    List<Appointment> findDueFor2hReminder(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT a FROM Appointment a WHERE a.status = 'BOOKED' AND a.createdAt <= :threshold")
    List<Appointment> findPendingAutoConfirm(@Param("threshold") Instant threshold);

    @Query("SELECT COUNT(a) > 0 FROM Appointment a WHERE a.doctor.user.id = :doctorUserId AND a.patient.id = :patientId")
    boolean existsByDoctorUserIdAndPatientId(@Param("doctorUserId") Long doctorUserId, @Param("patientId") Long patientId);
}
