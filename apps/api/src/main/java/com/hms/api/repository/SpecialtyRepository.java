package com.hms.api.repository;

import com.hms.api.domain.Specialty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {

    Optional<Specialty> findByCode(String code);

    boolean existsByCode(String code);

    Page<Specialty> findByIsActiveTrue(Pageable pageable);

    List<Specialty> findByIsActiveTrue();

    @org.springframework.data.jpa.repository.Query("SELECT s FROM Specialty s WHERE " +
           "(:isActive IS NULL OR s.isActive = :isActive) AND " +
           "(:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Specialty> searchSpecialties(
        @org.springframework.data.repository.query.Param("isActive") Boolean isActive,
        @org.springframework.data.repository.query.Param("search") String search,
        Pageable pageable
    );
}
