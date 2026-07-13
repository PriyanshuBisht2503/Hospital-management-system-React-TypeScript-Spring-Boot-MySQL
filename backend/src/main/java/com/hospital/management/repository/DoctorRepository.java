package com.hospital.management.repository;

import com.hospital.management.entity.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Doctor d WHERE d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"user", "department"})
    Optional<Doctor> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "department"})
    List<Doctor> findByDepartmentId(Long departmentId);

    @EntityGraph(attributePaths = {"user", "department"})
    @Query("SELECT d FROM Doctor d WHERE " +
           "LOWER(d.user.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(d.user.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(d.specialization) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Doctor> searchDoctors(@Param("query") String query, Pageable pageable);
}
