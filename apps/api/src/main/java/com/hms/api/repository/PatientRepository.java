package com.hms.api.repository;

import com.hms.api.domain.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    @Override
    @EntityGraph(attributePaths = {"user"})
    Optional<Patient> findById(Long id);

    @EntityGraph(attributePaths = {"user"})
    Optional<Patient> findByUserId(Long userId);

    Optional<Patient> findByPhone(String phone);

    Optional<Patient> findByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    @Query("SELECT p FROM Patient p WHERE " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(p.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "p.phone LIKE CONCAT('%', :query, '%') OR " +
           "LOWER(p.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Patient> searchPatients(@Param("query") String query, Pageable pageable);

    @Query("SELECT p FROM Patient p WHERE " +
           "EXISTS (SELECT 1 FROM Appointment a WHERE a.patient = p AND a.doctor.user.id = :doctorUserId) AND " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(p.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "p.phone LIKE CONCAT('%', :query, '%') OR " +
           "LOWER(p.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Patient> searchPatientsByDoctorUserId(@Param("doctorUserId") Long doctorUserId, @Param("query") String query, Pageable pageable);
}

