package com.hms.api.repository;

import com.hms.api.domain.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByCode(String code);

    boolean existsByCode(String code);

    Page<Department> findByIsActiveTrue(Pageable pageable);

    List<Department> findByIsActiveTrue();

    @org.springframework.data.jpa.repository.Query("SELECT d FROM Department d WHERE " +
           "(:isActive IS NULL OR d.isActive = :isActive) AND " +
           "(:search IS NULL OR LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(d.code) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Department> searchDepartments(
        @org.springframework.data.repository.query.Param("isActive") Boolean isActive,
        @org.springframework.data.repository.query.Param("search") String search,
        Pageable pageable
    );
}
