package com.aliator.cashregister.service;

import com.aliator.cashregister.api.ApiDtos.*;
import com.aliator.cashregister.domain.*;
import com.aliator.cashregister.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final MenuRepository menu = mock(MenuRepository.class);
    private final OrderService service = new OrderService(orders, menu);

    @Test void rejectsUnknownProducts() {
        when(orders.findByRequestId("request-123456789")).thenReturn(Optional.empty());
        when(menu.findAllById(any())).thenReturn(List.of());
        var req = new PlaceOrderRequest("T01", "request-123456789", List.of(new OrderItemRequest(99L, 1, "")));
        assertThrows(ResponseStatusException.class, () -> service.place(req));
    }

    @Test void idempotencyReturnsExistingOrder() {
        RestaurantOrder existing = new RestaurantOrder("request-123456789", "T01", List.of());
        when(orders.findByRequestId("request-123456789")).thenReturn(Optional.of(existing));
        var req = new PlaceOrderRequest("T01", "request-123456789", List.of(new OrderItemRequest(1L, 1, "")));
        assertEquals("T01", service.place(req).tableCode());
        verify(orders, never()).saveAndFlush(any());
    }

    @Test void cannotSkipNewToServed() {
        RestaurantOrder order = new RestaurantOrder("request-123456789", "T01", List.of());
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        assertThrows(ResponseStatusException.class, () -> service.changeStatus(1L, OrderStatus.SERVED));
    }

    @Test void allowsNewToPreparing() {
        RestaurantOrder order = new RestaurantOrder("request-123456789", "T01", List.of());
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        service.changeStatus(1L, OrderStatus.PREPARING);
        assertEquals(OrderStatus.PREPARING, order.getStatus());
    }
}
