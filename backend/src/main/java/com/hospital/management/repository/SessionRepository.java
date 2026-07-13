package com.hospital.management.repository;

import com.hospital.management.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, String> {
    Optional<Session> findBySessionToken(String sessionToken);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Session s SET s.active = false WHERE s.user.id = :userId")
    void invalidateAllUserSessions(@Param("userId") Long userId);
}
