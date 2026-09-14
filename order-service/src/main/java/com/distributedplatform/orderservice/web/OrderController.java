package com.distributedplatform.orderservice.web;

import com.distributedplatform.orderservice.Order;
import com.distributedplatform.orderservice.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder() {
        Order order = orderService.createOrder();
        return ResponseEntity.created(URI.create("/orders/" + order.getId())).body(order);
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable long id) {
        return orderService.getOrder(id);
    }
}
