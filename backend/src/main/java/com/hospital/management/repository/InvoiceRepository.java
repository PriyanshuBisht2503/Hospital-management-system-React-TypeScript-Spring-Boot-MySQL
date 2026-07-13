package com.hospital.management.repository;

import com.hospital.management.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @EntityGraph(attributePaths = {"patient", "patient.user"})
    Page<Invoice> findByPatientId(Long patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "patient.user"})
    List<Invoice> findByStatus(String status);
}
