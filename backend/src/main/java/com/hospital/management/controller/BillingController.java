package com.hospital.management.controller;

import com.hospital.management.dto.InvoiceDto;
import com.hospital.management.dto.PaymentRequest;
import com.hospital.management.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Validated
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/invoices/consultation")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<InvoiceDto> generateConsultationInvoice(
        @RequestParam("appointmentId") Long appointmentId,
        @RequestParam("fee") BigDecimal fee
    ) {
        InvoiceDto invoice = billingService.generateConsultationInvoice(appointmentId, fee);
        return ResponseEntity.status(HttpStatus.CREATED).body(invoice);
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<InvoiceDto> recordPayment(@Valid @RequestBody PaymentRequest request) {
        InvoiceDto invoice = billingService.recordPayment(request);
        return ResponseEntity.ok(invoice);
    }

    @GetMapping("/invoices/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST') or (hasRole('PATIENT') and #patientId == principal.id)")
    public ResponseEntity<Page<InvoiceDto>> getPatientInvoices(
        @PathVariable Long patientId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("billingDate").descending());
        return ResponseEntity.ok(billingService.getPatientInvoices(patientId, pageable));
    }
}
