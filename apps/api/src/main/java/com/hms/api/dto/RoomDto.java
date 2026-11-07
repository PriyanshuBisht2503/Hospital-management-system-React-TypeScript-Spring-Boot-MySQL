package com.hms.api.dto;

import com.hms.api.domain.Room;
import com.hms.api.domain.RoomType;

public record RoomDto(
    Long id,
    String roomNumber,
    Long branchId,
    String branchName,
    Long departmentId,
    String departmentName,
    RoomType type,
    boolean isActive
) {
    public static RoomDto from(Room r) {
        return new RoomDto(
            r.getId(),
            r.getRoomNumber(),
            r.getBranch().getId(),
            r.getBranch().getName(),
            r.getDepartment().getId(),
            r.getDepartment().getName(),
            r.getType(),
            r.isActive()
        );
    }
}
