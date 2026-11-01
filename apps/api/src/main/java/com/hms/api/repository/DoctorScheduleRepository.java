package com.hms.api.repository;

import com.hms.api.domain.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {

    List<DoctorSchedule> findByDoctorIdAndIsActiveTrue(Long doctorId);

    List<DoctorSchedule> findByDoctorIdAndDayOfWeekAndIsActiveTrue(Long doctorId, DayOfWeek dayOfWeek);

    List<DoctorSchedule> findByBranchIdAndIsActiveTrue(Long branchId);

    boolean existsByDoctorIdAndIsActiveTrue(Long doctorId);

    boolean existsByBranchIdAndIsActiveTrue(Long branchId);

    @Query("SELECT s FROM DoctorSchedule s WHERE s.doctor.id = :doctorId " +
           "AND s.dayOfWeek = :dayOfWeek " +
           "AND s.isActive = true " +
           "AND (:excludeId IS NULL OR s.id <> :excludeId) " +
           "AND s.startTime < :endTime AND s.endTime > :startTime")
    List<DoctorSchedule> findOverlappingSchedules(
            @Param("doctorId") Long doctorId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") Long excludeId
    );
}

