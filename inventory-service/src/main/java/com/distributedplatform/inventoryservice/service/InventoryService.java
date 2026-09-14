package com.distributedplatform.inventoryservice.service;

import com.distributedplatform.inventoryservice.dao.InventoryItemDto;
import com.distributedplatform.inventoryservice.dao.InventoryItemRepository;
import com.distributedplatform.inventoryservice.exception.InsufficientStockException;
import com.distributedplatform.inventoryservice.exception.InventoryItemNotFoundException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;

    public InventoryService(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

    public InventoryItemDto getBySku(String sku) {
        try {
            return inventoryItemRepository.findBySku(sku);
        } catch (EmptyResultDataAccessException e) {
            throw new InventoryItemNotFoundException(sku);
        }
    }

    /**
     * Deliberately naive: a separate read, then a separate write. @Transactional makes
     * these two statements commit or roll back together, but does NOT prevent two
     * concurrent calls from both reading the same quantity before either writes back —
     * a real lost-update race, left in on purpose for Phase 1 (see ROADMAP.md
     * Milestone 0.4).
     */
    @Transactional
    public InventoryItemDto reserve(String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reserve quantity must be positive: " + quantity);
        }

        InventoryItemDto item = getBySku(sku);

        if (item.getQuantity() < quantity) {
            throw new InsufficientStockException(sku, quantity, item.getQuantity());
        }

        int newQuantity = item.getQuantity() - quantity;
        inventoryItemRepository.updateQuantity(sku, newQuantity);

        item.setQuantity(newQuantity);
        return item;
    }
}
