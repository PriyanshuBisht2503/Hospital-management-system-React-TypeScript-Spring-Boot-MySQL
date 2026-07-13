package com.hospital.management.repository;

import com.hospital.management.entity.Medicine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MedicineRepository extends JpaRepository<Medicine, Long> {
    Optional<Medicine> findByNameIgnoreCase(String name);
    Page<Medicine> findByNameContainingIgnoreCaseOrBrandContainingIgnoreCase(String name, String brand, Pageable pageable);
}
