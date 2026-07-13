package com.hospital.management.repository;

import com.hospital.management.entity.InvoiceItem;
import com.hospital.management.entity.InvoiceItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {
    List<InvoiceItem> findByInvoiceId(Long invoiceId);
    boolean existsByItemTypeAndItemRefId(InvoiceItemType itemType, Long itemRefId);
}
