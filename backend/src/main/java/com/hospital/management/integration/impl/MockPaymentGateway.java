package com.hospital.management.integration.impl;

import com.hospital.management.integration.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
public class MockPaymentGateway implements PaymentGateway {
    @Override
    public boolean processCharge(Long invoiceId, BigDecimal amount, String paymentMethod, String transactionId) {
        log.info("Mock charge processed for Invoice ID: {} for amount: {} via {}", invoiceId, amount, paymentMethod);
        return true; // Simulate charge success
    }

    @Override
    public boolean processRefund(Long paymentId, BigDecimal amount) {
        log.info("Mock refund processed for Payment ID: {} for amount: {}", paymentId, amount);
        return true;
    }
}
