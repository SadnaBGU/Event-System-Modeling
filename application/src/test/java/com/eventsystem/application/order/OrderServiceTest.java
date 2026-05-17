package com.eventsystem.application.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eventsystem.application.appexceptions.AlreadyExistsOrderException;
import com.eventsystem.application.appexceptions.OrderNotFoundException;
import com.eventsystem.application.event.ZoneServicePort;
import com.eventsystem.application.lottery.ILotteryValidationPort;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderFactory;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.ZoneId;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private IActiveOrderRepository orderRepository;
    
    @Mock
    private ZoneServicePort zoneService;
    
    @Mock
    private OrderFactory orderFactory;

    @Mock
    private ILotteryValidationPort lotteryValidationPort;

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
        String resultId = orderService.createOrGetActiveOrder(testBuyer, EVENT_ID, Optional.empty());

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
        String resultId = orderService.createOrGetActiveOrder(testBuyer, EVENT_ID, Optional.empty());

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
        assertThrows(OrderNotFoundException.class, () -> {
            orderService.reserveSeat("INVALID_ORDER", "ZONE-A", "SEAT-1");
        });
    }

    @Test
    void reserveSeat_ValidOrder_AddsItemAndSaves() {
        // Arrange
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));
        
        OrderItem mockItem = new OrderItem("ZONE-A", "SEAT-1", 1, Money.of(new BigDecimal("100.0"), "USD"));
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
        
        OrderItem expiredItem = new OrderItem("ZONE-VIP", "SEAT-9", 1, Money.of(new BigDecimal("200.0"), "USD"));
        when(testOrder.expire()).thenReturn(List.of(expiredItem));

        // Act
        orderService.sweepExpiredOrders();

        // Assert
        verify(testOrder, times(1)).expire();
        verify(orderRepository, times(1)).save(testOrder);
        verify(zoneService, times(1)).releaseSeat(new ZoneId("ZONE-VIP"), new SeatId("SEAT-9"));
    }

    @Test
    void releaseSeat_ValidOrder_RemovesItemAndUnlocks() {
        // Arrange
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));

        // Act
        orderService.releaseSeat(ORDER_ID, "ZONE-A", "SEAT-1");

        // Assert
        verify(testOrder, times(1)).removeItem("ZONE-A", "SEAT-1");
        verify(orderRepository, times(1)).save(testOrder);
        verify(zoneService, times(1)).releaseSeat(new ZoneId("ZONE-A"), new SeatId("SEAT-1"));
    }

    @Test
    void createOrder_LotteryEvent_ValidCode_CreatesOrder() {
        // Arrange - UAT 17
        when(lotteryValidationPort.isLotteryEvent(EVENT_ID)).thenReturn(true);
        when(lotteryValidationPort.validateWinnerCode(EVENT_ID, testBuyer, "WINNER-123")).thenReturn(true);
        when(orderRepository.findByBuyerAndEvent(testBuyer, EVENT_ID)).thenReturn(Optional.empty());
        when(orderFactory.createOrder(eq(testBuyer), eq(EVENT_ID), any())).thenReturn(testOrder);

        // Act
        String resultId = orderService.createOrGetActiveOrder(testBuyer, EVENT_ID, Optional.of("WINNER-123"));

        // Assert
        assertEquals(ORDER_ID, resultId);
        verify(orderRepository, times(1)).save(testOrder);
    }

    @Test
    void createOrder_LotteryEvent_InvalidCode_ThrowsException() {
        // Arrange - UAT 18
        when(lotteryValidationPort.isLotteryEvent(EVENT_ID)).thenReturn(true);
        when(lotteryValidationPort.validateWinnerCode(EVENT_ID, testBuyer, "FAKE-CODE")).thenReturn(false);

        // Act & Assert
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            orderService.createOrGetActiveOrder(testBuyer, EVENT_ID, Optional.of("FAKE-CODE"));
        });
        
        assertEquals("Lottery authorization failed. Access denied.", exception.getMessage());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createNewOrderStrict_ExistingOrder_ThrowsException() {
        // Arrange - UAT 19
        when(orderRepository.findByBuyerAndEvent(testBuyer, EVENT_ID)).thenReturn(Optional.of(testOrder));
        when(testOrder.isExpired()).thenReturn(false);

        // Act & Assert
        assertThrows(AlreadyExistsOrderException.class, () -> {
            orderService.createNewOrderStrict(testBuyer, EVENT_ID);
        });
    }
}