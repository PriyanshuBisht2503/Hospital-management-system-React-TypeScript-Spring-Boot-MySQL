package com.hospital.management.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PaymentAlreadyCompletedException extends RuntimeException {
    public PaymentAlreadyCompletedException(String message) {
        super(message);
    }
}
