package com.distributedplatform.inventoryservice.dao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemDto {

    private Long id;
    private String sku;
    private Integer quantity;
    private LocalDateTime createdAt;
}