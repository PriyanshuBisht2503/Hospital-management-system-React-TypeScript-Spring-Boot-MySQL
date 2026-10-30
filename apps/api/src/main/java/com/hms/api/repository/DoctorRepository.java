package com.hms.api.repository;

import com.hms.api.domain.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    @EntityGraph(attributePaths = {"user", "specialties", "branches"})
    Optional<Doctor> findByIdAndIsActiveTrue(Long id);

    @EntityGraph(attributePaths = {"user", "specialties", "branches"})
    Optional<Doctor> findByUserId(Long userId);

    @Query("SELECT DISTINCT d FROM Doctor d " +
           "JOIN d.user u " +
           "LEFT JOIN d.specialties s " +
           "LEFT JOIN d.branches b " +
           "WHERE d.isActive = true AND " +
           "(:specialtyId IS NULL OR s.id = :specialtyId) AND " +
           "(:branchId IS NULL OR b.id = :branchId) AND " +
           "(:search IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(d.languages) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Doctor> searchActiveDoctors(
        @Param("specialtyId") Long specialtyId,
        @Param("branchId") Long branchId,
        @Param("search") String search,
        Pageable pageable
    );
}
