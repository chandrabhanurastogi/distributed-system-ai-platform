package com.distributedplatform.orderservice.dao;

import com.distributedplatform.orderservice.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Testcontainers
class OrderRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void save() {
        Order order = new Order(null, "CREATED", LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));

        Long generatedId = orderRepository.save(order);

        assertNotNull(generatedId);
    }

    @Test
    void findById() {
        LocalDateTime createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        Order order = new Order(null, "CREATED", createdAt);
        Long generatedId = orderRepository.save(order);

        Order found = orderRepository.findById(generatedId);

        assertEquals(generatedId, found.getId());
        assertEquals("CREATED", found.getStatus());
        assertEquals(createdAt, found.getCreatedAt());
    }
}
