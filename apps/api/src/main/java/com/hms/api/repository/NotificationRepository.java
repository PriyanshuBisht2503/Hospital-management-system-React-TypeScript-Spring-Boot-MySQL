package com.hms.api.repository;

import com.hms.api.domain.Notification;
import com.hms.api.domain.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByStatus(NotificationStatus status);

    @EntityGraph(attributePaths = {"patient", "user"})
    Page<Notification> findByPatientIdOrderByCreatedAtDesc(Long patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "user"})
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}

