package com.distributedplatform.inventoryservice.exception;

public class InventoryItemNotFoundException extends RuntimeException {

    public InventoryItemNotFoundException(String sku) {
        super("Inventory item not found: " + sku);
    }
}
