package com.hospital.management.dto;

import com.hospital.management.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvoiceDto(
    Long id,
    PatientDto patient,
    BigDecimal totalAmount,
    BigDecimal taxAmount,
    BigDecimal discountAmount,
    BigDecimal netAmount,
    InvoiceStatus status,
    LocalDateTime billingDate,
    LocalDateTime dueDate
) {}
