package com.distributedplatform.inventoryservice.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Testcontainers
class InventoryItemRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    InventoryItemRepository inventoryItemRepository;

    @Test
    void findById() {
        LocalDateTime createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        String sku = "sku-" + UUID.randomUUID();
        InventoryItemDto inventoryItemDto = new InventoryItemDto(null, sku, 1, createdAt);
        Long generatedId = inventoryItemRepository.save(inventoryItemDto);

        InventoryItemDto found = inventoryItemRepository.findById(generatedId);

        assertEquals(generatedId, found.getId());
        assertEquals(sku, found.getSku());
        assertEquals(1, found.getQuantity());
        assertEquals(createdAt, found.getCreatedAt());

    }

    @Test
    void save() {
        LocalDateTime createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        String sku = "sku-" + UUID.randomUUID();
        InventoryItemDto inventoryItemDto = new InventoryItemDto(null, sku, 1, createdAt);
        Long generatedId = inventoryItemRepository.save(inventoryItemDto);
        assertNotNull(generatedId);

    }
}