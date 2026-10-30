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
}
