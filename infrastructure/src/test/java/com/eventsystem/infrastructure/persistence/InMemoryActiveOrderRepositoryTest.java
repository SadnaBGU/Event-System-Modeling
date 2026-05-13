package com.eventsystem.infrastructure.persistence;

import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryActiveOrderRepositoryTest {

    private InMemoryActiveOrderRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryActiveOrderRepository();
    }

    @Test
    void findExpired_ReturnsOnlyExpiredOrders() {
        // Arrange
        ActiveOrder expired1 = mock(ActiveOrder.class);
        when(expired1.getOrderId()).thenReturn("ORD-1");
        when(expired1.isExpired()).thenReturn(true);

        ActiveOrder expired2 = mock(ActiveOrder.class);
        when(expired2.getOrderId()).thenReturn("ORD-2");
        when(expired2.isExpired()).thenReturn(true);

        ActiveOrder valid = mock(ActiveOrder.class);
        when(valid.getOrderId()).thenReturn("ORD-3");
        when(valid.isExpired()).thenReturn(false);

        repository.save(expired1);
        repository.save(expired2);
        repository.save(valid);

        // Act
        Optional<List<ActiveOrder>> result = repository.findExpired();

        // Assert
        assertTrue(result.isPresent());
        List<ActiveOrder> expiredList = result.get();
        assertEquals(2, expiredList.size());
        assertTrue(expiredList.contains(expired1));
        assertTrue(expiredList.contains(expired2));
        assertFalse(expiredList.contains(valid));
    }

    @Test
    void findByBuyerAndEvent_ReturnsCorrectOrder() {
        // Arrange
        BuyerReference targetBuyer = new BuyerReference(BuyerType.MEMBER, "sess-1", "user-123");
        BuyerReference wrongBuyer = new BuyerReference(BuyerType.MEMBER, "sess-2", "user-999");
        String targetEvent = "EVENT-A";
        String wrongEvent = "EVENT-B";

        // true order that matches both buyer and event
        ActiveOrder targetOrder = mock(ActiveOrder.class);
        when(targetOrder.getOrderId()).thenReturn("ORD-TARGET");
        when(targetOrder.getBuyerRef()).thenReturn(targetBuyer);
        when(targetOrder.getEventId()).thenReturn(targetEvent);

        // fake order with wrong buyer
        ActiveOrder wrongOrder1 = mock(ActiveOrder.class);
        when(wrongOrder1.getOrderId()).thenReturn("ORD-WRONG-1");
        when(wrongOrder1.getBuyerRef()).thenReturn(wrongBuyer);
        when(wrongOrder1.getEventId()).thenReturn(targetEvent);

        // fake order with wrong event
        ActiveOrder wrongOrder2 = mock(ActiveOrder.class);
        when(wrongOrder2.getOrderId()).thenReturn("ORD-WRONG-2");
        when(wrongOrder2.getBuyerRef()).thenReturn(targetBuyer);
        when(wrongOrder2.getEventId()).thenReturn(wrongEvent);

        repository.save(targetOrder);
        repository.save(wrongOrder1);
        repository.save(wrongOrder2);

        // Act
        Optional<ActiveOrder> result = repository.findByBuyerAndEvent(targetBuyer, targetEvent);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("ORD-TARGET", result.get().getOrderId());
    }
}