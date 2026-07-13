package com.hospital.management.repository;

import com.hospital.management.entity.MedicalRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user", "appointment"})
    Page<MedicalRecord> findByPatientId(Long patientId, Pageable pageable);
}
