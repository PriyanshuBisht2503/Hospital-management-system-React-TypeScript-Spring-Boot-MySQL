package com.hms.api.repository;

import com.hms.api.domain.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findTopByTargetOrderByCreatedAtDesc(String target);

    void deleteByTarget(String target);
}
