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

    boolean existsByBranchIdAndIsActiveTrue(Long branchId);

    boolean existsByDepartmentIdAndIsActiveTrue(Long departmentId);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Room r " +
           "JOIN FETCH r.branch b " +
           "JOIN FETCH r.department d " +
           "WHERE (:branchId IS NULL OR b.id = :branchId) AND " +
           "(:departmentId IS NULL OR d.id = :departmentId) AND " +
           "(:type IS NULL OR r.type = :type) AND " +
           "(:isActive IS NULL OR r.isActive = :isActive) AND " +
           "(:search IS NULL OR LOWER(r.roomNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Room> searchRooms(
        @org.springframework.data.repository.query.Param("branchId") Long branchId,
        @org.springframework.data.repository.query.Param("departmentId") Long departmentId,
        @org.springframework.data.repository.query.Param("type") com.hms.api.domain.RoomType type,
        @org.springframework.data.repository.query.Param("isActive") Boolean isActive,
        @org.springframework.data.repository.query.Param("search") String search,
        Pageable pageable
    );
}
