package com.hospital.management.mapper;

import com.hospital.management.dto.InvoiceDto;
import com.hospital.management.dto.PatientDto;
import com.hospital.management.entity.Invoice;
import com.hospital.management.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.annotation.processing.Generated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-11T23:45:39+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 23.0.1 (Oracle Corporation)"
)
@Component
public class InvoiceMapperImpl implements InvoiceMapper {

    @Autowired
    private PatientMapper patientMapper;

    @Override
    public InvoiceDto toDto(Invoice invoice) {
        if ( invoice == null ) {
            return null;
        }

        Long id = null;
        PatientDto patient = null;
        BigDecimal totalAmount = null;
        BigDecimal taxAmount = null;
        BigDecimal discountAmount = null;
        BigDecimal netAmount = null;
        InvoiceStatus status = null;
        LocalDateTime billingDate = null;
        LocalDateTime dueDate = null;

        id = invoice.getId();
        patient = patientMapper.toDto( invoice.getPatient() );
        totalAmount = invoice.getTotalAmount();
        taxAmount = invoice.getTaxAmount();
        discountAmount = invoice.getDiscountAmount();
        netAmount = invoice.getNetAmount();
        status = invoice.getStatus();
        billingDate = invoice.getBillingDate();
        dueDate = invoice.getDueDate();

        InvoiceDto invoiceDto = new InvoiceDto( id, patient, totalAmount, taxAmount, discountAmount, netAmount, status, billingDate, dueDate );

        return invoiceDto;
    }
}
