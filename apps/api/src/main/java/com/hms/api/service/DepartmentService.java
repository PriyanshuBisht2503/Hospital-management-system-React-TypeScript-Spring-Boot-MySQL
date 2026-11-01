package com.hms.api.service;

import com.hms.api.annotation.Audited;
import com.hms.api.domain.Department;
import com.hms.api.dto.DepartmentDto;
import com.hms.api.dto.DepartmentRequest;
import com.hms.api.dto.PageResponse;
import com.hms.api.exception.ConflictException;
import com.hms.api.exception.ResourceNotFoundException;
import com.hms.api.repository.DepartmentRepository;
import com.hms.api.repository.RoomRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final RoomRepository roomRepository;

    public DepartmentService(
            DepartmentRepository departmentRepository,
            RoomRepository roomRepository
    ) {
        this.departmentRepository = departmentRepository;
        this.roomRepository = roomRepository;
    }

    public PageResponse<DepartmentDto> getDepartments(String search, Boolean isActive, Pageable pageable) {
        Page<Department> page = departmentRepository.searchDepartments(isActive, search, pageable);
        return PageResponse.from(page.map(DepartmentDto::from));
    }

    public DepartmentDto getDepartmentById(Long id, boolean unauthenticated) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));

        if (unauthenticated && !dept.isActive()) {
            throw new ResourceNotFoundException("Department not found with id: " + id);
        }

        return DepartmentDto.from(dept);
    }

    @Transactional
    @Audited(action = "CREATE_DEPARTMENT", entityType = "DEPARTMENT")
    public DepartmentDto createDepartment(DepartmentRequest req) {
        if (departmentRepository.existsByCode(req.code())) {
            throw new ConflictException("Department with code '" + req.code() + "' already exists");
        }

        Department dept = new Department(req.name(), req.code(), req.description());
        Department saved = departmentRepository.save(dept);
        return DepartmentDto.from(saved);
    }

    @Transactional
    @Audited(action = "UPDATE_DEPARTMENT", entityType = "DEPARTMENT", entityIdParam = "id")
    public DepartmentDto updateDepartment(Long id, DepartmentRequest req) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));

        if (!dept.getCode().equalsIgnoreCase(req.code()) && departmentRepository.existsByCode(req.code())) {
            throw new ConflictException("Department with code '" + req.code() + "' already exists");
        }

        dept.setName(req.name());
        dept.setCode(req.code());
        dept.setDescription(req.description());

        Department saved = departmentRepository.save(dept);
        return DepartmentDto.from(saved);
    }

    @Transactional
    @Audited(action = "DEACTIVATE_DEPARTMENT", entityType = "DEPARTMENT", entityIdParam = "id")
    public void deactivateDepartment(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));

        if (roomRepository.existsByDepartmentIdAndIsActiveTrue(id)) {
            throw new ConflictException("Cannot deactivate department: referenced by active rooms");
        }

        dept.setActive(false);
        departmentRepository.save(dept);
    }
}
