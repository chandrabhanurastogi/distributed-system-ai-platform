package com.distributedplatform.orderservice.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(long id) {
        super("Order not found: " + id);
    }
}
