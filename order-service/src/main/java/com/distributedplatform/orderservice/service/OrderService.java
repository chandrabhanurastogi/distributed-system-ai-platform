package com.distributedplatform.orderservice.service;

import com.distributedplatform.orderservice.Order;
import com.distributedplatform.orderservice.dao.OrderRepository;
import com.distributedplatform.orderservice.exception.OrderNotFoundException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order createOrder() {
        Order order = new Order(null, "CREATED", LocalDateTime.now());
        Long id = orderRepository.save(order);
        order.setId(id);
        return order;
    }

    public Order getOrder(long id) {
        try {
            return orderRepository.findById(id);
        } catch (EmptyResultDataAccessException e) {
            throw new OrderNotFoundException(id);
        }
    }
}
