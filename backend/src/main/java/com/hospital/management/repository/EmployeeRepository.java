package com.hospital.management.repository;

import com.hospital.management.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @EntityGraph(attributePaths = {"user", "department"})
    Optional<Employee> findByEmployeeId(String employeeId);

    @EntityGraph(attributePaths = {"user", "department"})
    Optional<Employee> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "department"})
    @Query("SELECT e FROM Employee e WHERE " +
           "LOWER(e.user.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.user.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.jobTitle) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Employee> searchEmployees(@Param("query") String query, Pageable pageable);
}
