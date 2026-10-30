package com.hms.api.repository;

import com.hms.api.domain.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileRecordRepository extends JpaRepository<FileRecord, String> {

    Optional<FileRecord> findByStoredName(String storedName);
}
