package com.hospital.management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "insurance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Insurance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "provider_name", nullable = false, length = 150)
    private String providerName;

    @Column(name = "policy_number", nullable = false, unique = true, length = 50)
    private String policyNumber;

    @Column(name = "coverage_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal coverageLimit;

    @Column(name = "co_pay_percentage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal coPayPercentage = BigDecimal.ZERO;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
