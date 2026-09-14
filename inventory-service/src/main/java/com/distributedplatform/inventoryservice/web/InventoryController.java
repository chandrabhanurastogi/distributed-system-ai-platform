package com.distributedplatform.inventoryservice.web;

import com.distributedplatform.inventoryservice.dao.InventoryItemDto;
import com.distributedplatform.inventoryservice.service.InventoryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{sku}")
    public InventoryItemDto getBySku(@PathVariable String sku) {
        return inventoryService.getBySku(sku);
    }

    @PostMapping("/{sku}/reserve")
    public InventoryItemDto reserve(@PathVariable String sku, @RequestBody ReserveRequest request) {
        return inventoryService.reserve(sku, request.quantity());
    }
}
