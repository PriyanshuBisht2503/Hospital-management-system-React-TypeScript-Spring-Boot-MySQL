package com.hospital.management.serviceImpl;

import com.hospital.management.dto.InvoiceDto;
import com.hospital.management.dto.PaymentRequest;
import com.hospital.management.entity.*;
import com.hospital.management.exception.InvoiceAlreadyExistsException;
import com.hospital.management.exception.InvalidStateTransitionException;
import com.hospital.management.exception.ResourceNotFoundException;
import com.hospital.management.event.InvoiceGeneratedEvent;
import com.hospital.management.event.PaymentCompletedEvent;
import com.hospital.management.integration.PaymentGateway;
import com.hospital.management.mapper.InvoiceMapper;
import com.hospital.management.repository.*;
import com.hospital.management.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class BillingServiceImpl implements BillingService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final PaymentRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final InvoiceMapper invoiceMapper;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public InvoiceDto generateConsultationInvoice(Long appointmentId, BigDecimal consultationFee) {
        log.info("Request received to generate consultation invoice for Appointment ID: {}", appointmentId);
        
        // Idempotency check: prevent duplicate billing for same consultation appointment
        if (invoiceItemRepository.existsByItemTypeAndItemRefId(InvoiceItemType.CONSULTATION, appointmentId)) {
            log.warn("Invoice generation failed: consultation invoice already exists for Appointment ID {}", appointmentId);
            throw new InvoiceAlreadyExistsException("Consultation invoice already generated for this appointment");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> {
                log.warn("Invoice generation failed: Appointment ID {} not found", appointmentId);
                return new ResourceNotFoundException("Appointment not found with id: " + appointmentId);
            });

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            log.warn("Invoice generation failed: Appointment ID {} is cancelled", appointmentId);
            throw new InvalidStateTransitionException("Cannot generate billing for a cancelled appointment");
        }

        BigDecimal taxRate = new BigDecimal("18.00"); // 18% GST standard
        BigDecimal taxMultiplier = taxRate.divide(new BigDecimal("100.00"), 4, RoundingMode.HALF_UP);
        BigDecimal taxAmount = consultationFee.multiply(taxMultiplier).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = consultationFee.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        Invoice invoice = Invoice.builder()
            .patient(appointment.getPatient())
            .totalAmount(consultationFee)
            .taxAmount(taxAmount)
            .discountAmount(BigDecimal.ZERO)
            .netAmount(netAmount)
            .status(InvoiceStatus.UNPAID)
            .dueDate(LocalDateTime.now().plusDays(15))
            .build();

        invoice = invoiceRepository.save(invoice);

        InvoiceItem item = InvoiceItem.builder()
            .invoice(invoice)
            .itemType(InvoiceItemType.CONSULTATION)
            .itemRefId(appointment.getId())
            .quantity(1)
            .unitPrice(consultationFee)
            .taxRate(taxRate)
            .taxAmount(taxAmount)
            .totalAmount(netAmount)
            .build();

        invoiceItemRepository.save(item);
        log.info("Consultation invoice generated successfully with ID: {} for net amount: {}", invoice.getId(), netAmount);

        // Publish Domain Event
        eventPublisher.publishEvent(new InvoiceGeneratedEvent(
            invoice.getId(),
            invoice.getPatient().getId(),
            invoice.getNetAmount()
        ));

        return invoiceMapper.toDto(invoice);
    }

    @Override
    public InvoiceDto recordPayment(PaymentRequest request) {
        log.info("Processing payment registration for Invoice ID: {} for amount: {}", request.invoiceId(), request.amount());
        Invoice invoice = invoiceRepository.findById(request.invoiceId())
            .orElseThrow(() -> {
                log.warn("Payment failed: Invoice ID {} not found", request.invoiceId());
                return new ResourceNotFoundException("Invoice not found with id: " + request.invoiceId());
            });

        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.CANCELLED) {
            log.warn("Payment failed: Invoice ID {} is already paid or cancelled", request.invoiceId());
            throw new InvalidStateTransitionException("Invoice is already paid or cancelled");
        }

        // External Integration: Charge via payment gateway
        boolean gatewaySuccess = paymentGateway.processCharge(
            invoice.getId(), 
            request.amount(), 
            request.paymentMethod().name(), 
            request.transactionId()
        );

        if (!gatewaySuccess) {
            log.error("Payment failed: Gateway charge processing failed for Invoice ID {}", invoice.getId());
            throw new InvalidStateTransitionException("Payment gateway processing failed");
        }

        // Record Payment Log
        Payment payment = Payment.builder()
            .invoice(invoice)
            .amount(request.amount())
            .paymentMethod(request.paymentMethod())
            .transactionId(request.transactionId())
            .status(PaymentStatus.SUCCESS)
            .build();

        payment = paymentRepository.save(payment);

        // Update Invoice status
        if (request.amount().compareTo(invoice.getNetAmount()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }

        invoice = invoiceRepository.save(invoice);
        log.info("Payment recorded successfully. Invoice ID {} status set to: {}", invoice.getId(), invoice.getStatus());

        // Publish Domain Event
        eventPublisher.publishEvent(new PaymentCompletedEvent(
            payment.getId(),
            invoice.getId(),
            payment.getAmount(),
            payment.getPaymentMethod().name(),
            payment.getTransactionId()
        ));

        return invoiceMapper.toDto(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InvoiceDto> getPatientInvoices(Long patientId, Pageable pageable) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient not found with id: " + patientId);
        }
        return invoiceRepository.findByPatientId(patientId, pageable).map(invoiceMapper::toDto);
    }
}
