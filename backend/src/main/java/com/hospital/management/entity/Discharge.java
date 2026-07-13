package com.hospital.management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "discharges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Discharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id", nullable = false, unique = true)
    private Admission admission;

    @Column(name = "discharge_date", nullable = false)
    @Builder.Default
    private LocalDateTime dischargeDate = LocalDateTime.now();

    @Column(name = "discharge_summary", columnDefinition = "TEXT")
    private String dischargeSummary;

    @Column(name = "final_invoice_id")
    private Long finalInvoiceId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private DischargeStatus status = DischargeStatus.COMPLETED;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
