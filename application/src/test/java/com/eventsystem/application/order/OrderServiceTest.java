package com.eventsystem.application.order;

import com.eventsystem.application.event.ZoneServicePort;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderFactory;
import com.eventsystem.domain.order.OrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.SeatId;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private ActiveOrderRepository orderRepository;
    
    @Mock
    private ZoneServicePort zoneService;
    
    @Mock
    private OrderFactory orderFactory;

    @InjectMocks
    private OrderService orderService;

    private BuyerReference testBuyer;
    private ActiveOrder testOrder;
    private final String EVENT_ID = "EVENT-777";
    private final String ORDER_ID = "ORDER-888";

    @BeforeEach
    void setUp() {
        testBuyer = new BuyerReference(BuyerType.MEMBER, "sess-1", "user-123");
        testOrder = mock(ActiveOrder.class);
        lenient().when(testOrder.getOrderId()).thenReturn(ORDER_ID);
    }

    @Test
    void createOrGetActiveOrder_ExistingValidOrder_ReturnsExistingId() {
        // Arrange
        when(orderRepository.findByBuyerAndEvent(testBuyer, EVENT_ID)).thenReturn(Optional.of(testOrder));
        when(testOrder.isExpired()).thenReturn(false);

        // Act
        String resultId = orderService.createOrGetActiveOrder(testBuyer, EVENT_ID);

        // Assert
        assertEquals(ORDER_ID, resultId);
        verify(orderFactory, never()).createOrder(any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrGetActiveOrder_NoOrderExists_CreatesNewOrder() {
        // Arrange
        when(orderRepository.findByBuyerAndEvent(testBuyer, EVENT_ID)).thenReturn(Optional.empty());
        
        when(orderFactory.createOrder(eq(testBuyer), eq(EVENT_ID), any(Instant.class))).thenReturn(testOrder);

        // Act
        String resultId = orderService.createOrGetActiveOrder(testBuyer, EVENT_ID);

        // Assert
        assertEquals(ORDER_ID, resultId);
        verify(orderFactory, times(1)).createOrder(eq(testBuyer), eq(EVENT_ID), any(Instant.class));
        verify(orderRepository, times(1)).save(testOrder);
    }

    @Test
    void reserveSeat_OrderNotFound_ThrowsException() {
        // Arrange
        when(orderRepository.findById("INVALID_ORDER")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.reserveSeat("INVALID_ORDER", "ZONE-A", "SEAT-1");
        });
        assertEquals("Active order not found", exception.getMessage());
    }

    @Test
    void reserveSeat_ValidOrder_AddsItemAndSaves() {
        // Arrange
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));
        
        OrderItem mockItem = new OrderItem("ZONE-A", "SEAT-1", 1, new BigDecimal("100.0"));
        when(zoneService.reserveSeat(new ZoneId("ZONE-A"), new SeatId("SEAT-1"))).thenReturn(mockItem);

        // Act
        orderService.reserveSeat(ORDER_ID, "ZONE-A", "SEAT-1");

        // Assert
        verify(zoneService, times(1)).reserveSeat(new ZoneId("ZONE-A"), new SeatId("SEAT-1"));
        verify(testOrder, times(1)).addItem(mockItem);
        verify(orderRepository, times(1)).save(testOrder);
    }

    @Test
    void sweepExpiredOrders_ProcessesOrdersAndUnlocksSeats() {
        // Arrange
        when(orderRepository.findExpired()).thenReturn(Optional.of(List.of(testOrder)));
        
        OrderItem expiredItem = new OrderItem("ZONE-VIP", "SEAT-9", 1, new BigDecimal("200.0"));
        when(testOrder.expire()).thenReturn(List.of(expiredItem));

        // Act
        orderService.sweepExpiredOrders();

        // Assert
        verify(testOrder, times(1)).expire(); // מוודאים שהעגלה שינתה סטטוס
        verify(orderRepository, times(1)).save(testOrder); // מוודאים שהיא נשמרה כ-EXPIRED
        verify(zoneService, times(1)).releaseSeat(new ZoneId("ZONE-VIP"), new SeatId("SEAT-9")); // מוודאים שהכיסא שוחרר למלאי
    }
}