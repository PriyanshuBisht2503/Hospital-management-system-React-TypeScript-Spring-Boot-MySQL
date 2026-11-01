package com.hms.api.service;

import com.hms.api.annotation.Audited;
import com.hms.api.domain.Branch;
import com.hms.api.dto.BranchDto;
import com.hms.api.dto.BranchRequest;
import com.hms.api.dto.PageResponse;
import com.hms.api.exception.ConflictException;
import com.hms.api.exception.ResourceNotFoundException;
import com.hms.api.repository.BranchRepository;
import com.hms.api.repository.DoctorRepository;
import com.hms.api.repository.RoomRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchService {

    private final BranchRepository branchRepository;
    private final DoctorRepository doctorRepository;
    private final RoomRepository roomRepository;

    public BranchService(
            BranchRepository branchRepository,
            DoctorRepository doctorRepository,
            RoomRepository roomRepository
    ) {
        this.branchRepository = branchRepository;
        this.doctorRepository = doctorRepository;
        this.roomRepository = roomRepository;
    }

    public PageResponse<BranchDto> getBranches(String search, Boolean isActive, Pageable pageable) {
        Page<Branch> page = branchRepository.searchBranches(isActive, search, pageable);
        return PageResponse.from(page.map(BranchDto::from));
    }

    public BranchDto getBranchById(Long id, boolean unauthenticated) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + id));

        if (unauthenticated && !branch.isActive()) {
            throw new ResourceNotFoundException("Branch not found with id: " + id);
        }

        return BranchDto.from(branch);
    }

    @Transactional
    @Audited(action = "CREATE_BRANCH", entityType = "BRANCH")
    public BranchDto createBranch(BranchRequest req) {
        if (branchRepository.existsByCode(req.code())) {
            throw new ConflictException("Branch with code '" + req.code() + "' already exists");
        }

        Branch branch = new Branch(
            req.name(),
            req.code(),
            req.address(),
            req.city(),
            req.phone(),
            req.email()
        );

        Branch saved = branchRepository.save(branch);
        return BranchDto.from(saved);
    }

    @Transactional
    @Audited(action = "UPDATE_BRANCH", entityType = "BRANCH", entityIdParam = "id")
    public BranchDto updateBranch(Long id, BranchRequest req) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + id));

        if (!branch.getCode().equalsIgnoreCase(req.code()) && branchRepository.existsByCode(req.code())) {
            throw new ConflictException("Branch with code '" + req.code() + "' already exists");
        }

        branch.setName(req.name());
        branch.setCode(req.code());
        branch.setAddress(req.address());
        branch.setCity(req.city());
        branch.setPhone(req.phone());
        branch.setEmail(req.email());

        Branch saved = branchRepository.save(branch);
        return BranchDto.from(saved);
    }

    @Transactional
    @Audited(action = "DEACTIVATE_BRANCH", entityType = "BRANCH", entityIdParam = "id")
    public void deactivateBranch(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + id));

        if (doctorRepository.existsByBranches_IdAndIsActiveTrue(id)) {
            throw new ConflictException("Cannot deactivate branch: referenced by active doctors");
        }

        if (roomRepository.existsByBranchIdAndIsActiveTrue(id)) {
            throw new ConflictException("Cannot deactivate branch: referenced by active rooms");
        }

        branch.setActive(false);
        branchRepository.save(branch);
    }
}

