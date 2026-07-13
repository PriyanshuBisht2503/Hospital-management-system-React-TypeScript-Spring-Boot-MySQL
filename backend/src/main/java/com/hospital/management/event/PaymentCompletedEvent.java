package com.hospital.management.event;

import java.math.BigDecimal;

public record PaymentCompletedEvent(
    Long paymentId,
    Long invoiceId,
    BigDecimal amount,
    String paymentMethod,
    String transactionId
) {}
