package com.hospital.management.dto;

import com.hospital.management.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PaymentRequest(
    @NotNull(message = "Invoice ID is required")
    Long invoiceId,

    @NotNull(message = "Payment amount is required")
    @Positive(message = "Payment amount must be positive")
    BigDecimal amount,

    @NotNull(message = "Payment method is required")
    PaymentMethod paymentMethod,

    String transactionId
) {}
