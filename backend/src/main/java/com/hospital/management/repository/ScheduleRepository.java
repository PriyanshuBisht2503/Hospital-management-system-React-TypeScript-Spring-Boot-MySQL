package com.hospital.management.repository;

import com.hospital.management.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findByDoctorId(Long doctorId);

    @Query("SELECT s FROM Schedule s WHERE s.doctor.id = :doctorId AND s.dayOfWeek = :dayOfWeek")
    List<Schedule> findByDoctorIdAndDayOfWeek(@Param("doctorId") Long doctorId, @Param("dayOfWeek") int dayOfWeek);
}
