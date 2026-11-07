package com.hms.api.service;

import com.hms.api.annotation.Audited;
import com.hms.api.domain.Branch;
import com.hms.api.domain.Department;
import com.hms.api.domain.Room;
import com.hms.api.domain.RoomType;
import com.hms.api.dto.PageResponse;
import com.hms.api.dto.RoomDto;
import com.hms.api.dto.RoomRequest;
import com.hms.api.exception.ConflictException;
import com.hms.api.exception.ResourceNotFoundException;
import com.hms.api.repository.BranchRepository;
import com.hms.api.repository.DepartmentRepository;
import com.hms.api.repository.RoomRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final BranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;

    public RoomService(
            RoomRepository roomRepository,
            BranchRepository branchRepository,
            DepartmentRepository departmentRepository
    ) {
        this.roomRepository = roomRepository;
        this.branchRepository = branchRepository;
        this.departmentRepository = departmentRepository;
    }

    public PageResponse<RoomDto> getRooms(
            Long branchId,
            Long departmentId,
            RoomType type,
            String search,
            Boolean isActive,
            Pageable pageable
    ) {
        Page<Room> page = roomRepository.searchRooms(branchId, departmentId, type, isActive, search, pageable);
        return PageResponse.from(page.map(RoomDto::from));
    }

    public RoomDto getRoomById(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + id));
        return RoomDto.from(room);
    }

    @Transactional
    @Audited(action = "CREATE_ROOM", entityType = "ROOM")
    public RoomDto createRoom(RoomRequest req) {
        Branch branch = branchRepository.findById(req.branchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + req.branchId()));

        Department department = departmentRepository.findById(req.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + req.departmentId()));

        if (roomRepository.existsByBranchIdAndRoomNumber(branch.getId(), req.roomNumber())) {
            throw new ConflictException("Room with number '" + req.roomNumber() + "' already exists in branch '" + branch.getName() + "'");
        }

        Room room = new Room(req.roomNumber(), branch, department, req.type());
        Room saved = roomRepository.save(room);
        return RoomDto.from(saved);
    }

    @Transactional
    @Audited(action = "UPDATE_ROOM", entityType = "ROOM", entityIdParam = "id")
    public RoomDto updateRoom(Long id, RoomRequest req) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + id));

        Branch branch = branchRepository.findById(req.branchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + req.branchId()));

        Department department = departmentRepository.findById(req.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + req.departmentId()));

        if ((!room.getBranch().getId().equals(branch.getId()) || !room.getRoomNumber().equalsIgnoreCase(req.roomNumber()))
                && roomRepository.existsByBranchIdAndRoomNumber(branch.getId(), req.roomNumber())) {
            throw new ConflictException("Room with number '" + req.roomNumber() + "' already exists in branch '" + branch.getName() + "'");
        }

        room.setBranch(branch);
        room.setDepartment(department);
        room.setRoomNumber(req.roomNumber());
        room.setType(req.type());

        Room saved = roomRepository.save(room);
        return RoomDto.from(saved);
    }

    @Transactional
    @Audited(action = "DEACTIVATE_ROOM", entityType = "ROOM", entityIdParam = "id")
    public void deactivateRoom(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + id));

        room.setActive(false);
        roomRepository.save(room);
    }
}
