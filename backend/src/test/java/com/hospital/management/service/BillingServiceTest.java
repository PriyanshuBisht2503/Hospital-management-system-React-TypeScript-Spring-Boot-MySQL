package com.hospital.management.service;

import com.hospital.management.dto.InvoiceDto;
import com.hospital.management.dto.PaymentRequest;
import com.hospital.management.entity.*;
import com.hospital.management.exception.InvoiceAlreadyExistsException;
import com.hospital.management.exception.InvalidStateTransitionException;
import com.hospital.management.event.InvoiceGeneratedEvent;
import com.hospital.management.event.PaymentCompletedEvent;
import com.hospital.management.integration.PaymentGateway;
import com.hospital.management.mapper.InvoiceMapper;
import com.hospital.management.repository.*;
import com.hospital.management.serviceImpl.BillingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BillingServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceItemRepository invoiceItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private InvoiceMapper invoiceMapper;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BillingServiceImpl billingService;

    private Appointment testAppointment;
    private Invoice testInvoice;
    private Patient testPatient;

    @BeforeEach
    public void setup() {
        testPatient = Patient.builder().id(1L).build();
        testAppointment = Appointment.builder().id(1L).patient(testPatient).status(AppointmentStatus.CONFIRMED).build();
        testInvoice = Invoice.builder()
            .id(1L)
            .patient(testPatient)
            .totalAmount(new BigDecimal("500.00"))
            .taxAmount(new BigDecimal("90.00"))
            .netAmount(new BigDecimal("590.00"))
            .status(InvoiceStatus.UNPAID)
            .build();
    }

    @Test
    public void testGenerateConsultationInvoice_Success() {
        when(invoiceItemRepository.existsByItemTypeAndItemRefId(InvoiceItemType.CONSULTATION, 1L)).thenReturn(false);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(testInvoice);
        when(invoiceMapper.toDto(any(Invoice.class))).thenReturn(new InvoiceDto(1L, null, new BigDecimal("500.00"), new BigDecimal("90.00"), new BigDecimal("0.00"), new BigDecimal("590.00"), InvoiceStatus.UNPAID, null, null));

        InvoiceDto result = billingService.generateConsultationInvoice(1L, new BigDecimal("500.00"));

        assertNotNull(result);
        assertEquals("UNPAID", result.status().name());
        verify(invoiceRepository, times(1)).save(any(Invoice.class));
        verify(invoiceItemRepository, times(1)).save(any(InvoiceItem.class));
        verify(eventPublisher, times(1)).publishEvent(any(InvoiceGeneratedEvent.class));
    }

    @Test
    public void testGenerateConsultationInvoice_Failure_AlreadyExists() {
        when(invoiceItemRepository.existsByItemTypeAndItemRefId(InvoiceItemType.CONSULTATION, 1L)).thenReturn(true);

        assertThrows(InvoiceAlreadyExistsException.class, () -> billingService.generateConsultationInvoice(1L, new BigDecimal("500.00")));
    }

    @Test
    public void testRecordPayment_Success() {
        PaymentRequest request = new PaymentRequest(1L, new BigDecimal("590.00"), PaymentMethod.ONLINE, "TXN-12345");

        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(testInvoice));
        when(paymentGateway.processCharge(1L, new BigDecimal("590.00"), "ONLINE", "TXN-12345")).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(testInvoice);
        when(invoiceMapper.toDto(any(Invoice.class))).thenReturn(new InvoiceDto(1L, null, new BigDecimal("500.00"), new BigDecimal("90.00"), new BigDecimal("0.00"), new BigDecimal("590.00"), InvoiceStatus.PAID, null, null));

        InvoiceDto result = billingService.recordPayment(request);

        assertNotNull(result);
        assertEquals("PAID", result.status().name());
        assertEquals(InvoiceStatus.PAID, testInvoice.getStatus());
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(eventPublisher, times(1)).publishEvent(any(PaymentCompletedEvent.class));
    }

    @Test
    public void testRecordPayment_Failure_AlreadyPaid() {
        testInvoice.setStatus(InvoiceStatus.PAID);
        PaymentRequest request = new PaymentRequest(1L, new BigDecimal("590.00"), PaymentMethod.ONLINE, "TXN-12345");

        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(testInvoice));

        assertThrows(InvalidStateTransitionException.class, () -> billingService.recordPayment(request));
    }
}
