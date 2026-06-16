package com.footballsystem.repository;

import com.footballsystem.model.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    List<InventoryItem> findByActiveTrue();
    List<InventoryItem> findByActiveTrueOrderByCreatedAtDesc();
}
