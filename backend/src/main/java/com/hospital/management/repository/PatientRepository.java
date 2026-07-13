package com.hospital.management.repository;

import com.hospital.management.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {

    @EntityGraph(attributePaths = {"user"})
    Optional<Patient> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT p FROM Patient p WHERE " +
           "LOWER(p.user.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.user.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.user.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.bloodGroup) = LOWER(:query)")
    Page<Patient> searchPatients(@Param("query") String query, Pageable pageable);
}
