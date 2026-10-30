package com.hms.api.repository;

import com.hms.api.domain.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    Page<Room> findByIsActiveTrue(Pageable pageable);

    Page<Room> findByBranchIdAndIsActiveTrue(Long branchId, Pageable pageable);

    Optional<Room> findByBranchIdAndRoomNumber(Long branchId, String roomNumber);

    boolean existsByBranchIdAndRoomNumber(Long branchId, String roomNumber);
}
