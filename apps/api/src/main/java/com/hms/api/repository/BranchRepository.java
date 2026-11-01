package com.hms.api.repository;

import com.hms.api.domain.Branch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    Optional<Branch> findByCode(String code);

    boolean existsByCode(String code);

    Page<Branch> findByIsActiveTrue(Pageable pageable);

    List<Branch> findByIsActiveTrue();

    @org.springframework.data.jpa.repository.Query("SELECT b FROM Branch b WHERE " +
           "(:isActive IS NULL OR b.isActive = :isActive) AND " +
           "(:search IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(b.code) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(b.city) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Branch> searchBranches(
        @org.springframework.data.repository.query.Param("isActive") Boolean isActive,
        @org.springframework.data.repository.query.Param("search") String search,
        Pageable pageable
    );
}
