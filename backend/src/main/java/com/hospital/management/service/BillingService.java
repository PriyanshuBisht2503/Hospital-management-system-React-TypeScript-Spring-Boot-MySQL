package com.hospital.management.service;

import com.hospital.management.dto.InvoiceDto;
import com.hospital.management.dto.PaymentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface BillingService {
    InvoiceDto generateConsultationInvoice(Long appointmentId, BigDecimal consultationFee);
    InvoiceDto recordPayment(PaymentRequest request);
    Page<InvoiceDto> getPatientInvoices(Long patientId, Pageable pageable);
}
