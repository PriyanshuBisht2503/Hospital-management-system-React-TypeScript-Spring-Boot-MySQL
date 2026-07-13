package com.hospital.management.event;

import java.math.BigDecimal;

public record InvoiceGeneratedEvent(
    Long invoiceId,
    Long patientId,
    BigDecimal netAmount
) {}
