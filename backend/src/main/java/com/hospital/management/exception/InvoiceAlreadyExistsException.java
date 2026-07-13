package com.hospital.management.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvoiceAlreadyExistsException extends RuntimeException {
    public InvoiceAlreadyExistsException(String message) {
        super(message);
    }
}
