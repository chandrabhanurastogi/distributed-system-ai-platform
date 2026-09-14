package com.distributedplatform.inventoryservice.service;

import com.distributedplatform.inventoryservice.dao.InventoryItemDto;
import com.distributedplatform.inventoryservice.dao.InventoryItemRepository;
import com.distributedplatform.inventoryservice.exception.InsufficientStockException;
import com.distributedplatform.inventoryservice.exception.InventoryItemNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Test
    void getBySku_returnsItemWhenFound() {
        InventoryService inventoryService = new InventoryService(inventoryItemRepository);
        InventoryItemDto existing = new InventoryItemDto(1L, "SKU-1", 5, LocalDateTime.now());
        when(inventoryItemRepository.findBySku("SKU-1")).thenReturn(existing);

        InventoryItemDto result = inventoryService.getBySku("SKU-1");

        assertThat(result).isSameAs(existing);
    }

    @Test
    void getBySku_throwsNotFoundWhenMissing() {
        InventoryService inventoryService = new InventoryService(inventoryItemRepository);
        when(inventoryItemRepository.findBySku("MISSING")).thenThrow(new EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> inventoryService.getBySku("MISSING"))
                .isInstanceOf(InventoryItemNotFoundException.class);
    }

    @Test
    void reserve_decrementsQuantityWhenSufficientStock() {
        InventoryService inventoryService = new InventoryService(inventoryItemRepository);
        InventoryItemDto existing = new InventoryItemDto(1L, "SKU-1", 5, LocalDateTime.now());
        when(inventoryItemRepository.findBySku("SKU-1")).thenReturn(existing);

        InventoryItemDto result = inventoryService.reserve("SKU-1", 3);

        assertThat(result.getQuantity()).isEqualTo(2);
        verify(inventoryItemRepository).updateQuantity("SKU-1", 2);
    }

    @Test
    void reserve_throwsInsufficientStockWhenRequestExceedsAvailable() {
        InventoryService inventoryService = new InventoryService(inventoryItemRepository);
        InventoryItemDto existing = new InventoryItemDto(1L, "SKU-1", 2, LocalDateTime.now());
        when(inventoryItemRepository.findBySku("SKU-1")).thenReturn(existing);

        assertThatThrownBy(() -> inventoryService.reserve("SKU-1", 10))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void reserve_rejectsNonPositiveQuantity() {
        InventoryService inventoryService = new InventoryService(inventoryItemRepository);

        assertThatThrownBy(() -> inventoryService.reserve("SKU-1", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
