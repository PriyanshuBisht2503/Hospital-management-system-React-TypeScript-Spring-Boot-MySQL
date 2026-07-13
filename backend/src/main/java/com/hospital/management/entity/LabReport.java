package com.hospital.management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lab_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_order_id", nullable = false)
    private LabOrder labOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id", nullable = false)
    private LabTest labTest;

    @Column(name = "result_value")
    private String resultValue;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private LabReportStatus status = LabReportStatus.PRELIMINARY;

    @Column(name = "pdf_report_path")
    private String pdfReportPath;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "uploaded_at", insertable = false, updatable = false)
    private LocalDateTime uploadedAt;
}
