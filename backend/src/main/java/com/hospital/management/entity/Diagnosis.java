package com.hospital.management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "diagnoses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Diagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_record_id", nullable = false)
    private MedicalRecord medicalRecord;

    @Column(name = "disease_code", nullable = false, length = 10)
    private String diseaseCode;

    @Column(name = "disease_name", nullable = false, length = 150)
    private String diseaseName;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private DiagnosisSeverity severity = DiagnosisSeverity.MODERATE;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
