package com.hospital.management.repository;

import com.hospital.management.entity.MedicineInventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicineInventoryRepository extends JpaRepository<MedicineInventory, Long> {

    @EntityGraph(attributePaths = {"medicine"})
    List<MedicineInventory> findByMedicineId(Long medicineId);

    @EntityGraph(attributePaths = {"medicine"})
    @Query("SELECT mi FROM MedicineInventory mi WHERE mi.stockQuantity <= mi.safetyThreshold")
    Page<MedicineInventory> findLowStockMedicines(Pageable pageable);
}
