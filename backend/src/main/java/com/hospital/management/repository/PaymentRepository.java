package com.hospital.management.repository;

import com.hospital.management.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"invoice", "invoice.patient", "invoice.patient.user"})
    List<Payment> findByInvoiceId(Long invoiceId);

    @EntityGraph(attributePaths = {"invoice", "invoice.patient", "invoice.patient.user"})
    Page<Payment> findByTransactionId(String transactionId, Pageable pageable);
}
