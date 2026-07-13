package com.hospital.management.mapper;

import com.hospital.management.dto.InvoiceDto;
import com.hospital.management.entity.Invoice;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {PatientMapper.class})
public interface InvoiceMapper {
    InvoiceDto toDto(Invoice invoice);
}
