package com.hms.api.repository;

import com.hms.api.domain.ScheduleException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleExceptionRepository extends JpaRepository<ScheduleException, Long> {

    List<ScheduleException> findByDoctorIdOrderByDateDesc(Long doctorId);

    Optional<ScheduleException> findByIdAndDoctorId(Long id, Long doctorId);

    List<ScheduleException> findByDoctorIdAndDate(Long doctorId, LocalDate date);

    List<ScheduleException> findByDoctorIdAndDateBetween(Long doctorId, LocalDate fromDate, LocalDate toDate);

    @Query("SELECT e FROM ScheduleException e WHERE e.doctor.id = :doctorId " +
           "AND e.date = :date AND e.type IN ('LEAVE', 'HOLIDAY')")
    List<ScheduleException> findLeavesOnDate(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);
}

