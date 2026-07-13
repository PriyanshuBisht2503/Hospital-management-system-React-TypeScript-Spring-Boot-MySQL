package com.hospital.management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType;

    @Column(name = "old_value", columnDefinition = "JSON")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "JSON")
    private String newValue;

    @Column(name = "modified_by", nullable = false, length = 100)
    private String modifiedBy;

    @Column(name = "modified_at", insertable = false, updatable = false)
    private LocalDateTime modifiedAt;
}
