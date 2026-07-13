package com.hospital.management.integration;

import java.math.BigDecimal;

public interface PaymentGateway {
    boolean processCharge(Long invoiceId, BigDecimal amount, String paymentMethod, String transactionId);
    boolean processRefund(Long paymentId, BigDecimal amount);
}
