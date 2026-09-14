package com.distributedplatform.orderservice.service;

import com.distributedplatform.orderservice.Order;
import com.distributedplatform.orderservice.dao.OrderRepository;
import com.distributedplatform.orderservice.exception.OrderNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Test
    void createOrder_savesAndReturnsOrderWithGeneratedId() {
        OrderService orderService = new OrderService(orderRepository);
        when(orderRepository.save(any(Order.class))).thenReturn(42L);

        Order created = orderService.createOrder();

        assertThat(created.getId()).isEqualTo(42L);
        assertThat(created.getStatus()).isEqualTo("CREATED");
        assertThat(created.getCreatedAt()).isNotNull();
    }

    @Test
    void getOrder_returnsOrderWhenFound() {
        OrderService orderService = new OrderService(orderRepository);
        Order existing = new Order(1L, "CREATED", null);
        when(orderRepository.findById(1L)).thenReturn(existing);

        Order result = orderService.getOrder(1L);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void getOrder_throwsOrderNotFoundWhenMissing() {
        OrderService orderService = new OrderService(orderRepository);
        when(orderRepository.findById(99L)).thenThrow(new EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> orderService.getOrder(99L))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
