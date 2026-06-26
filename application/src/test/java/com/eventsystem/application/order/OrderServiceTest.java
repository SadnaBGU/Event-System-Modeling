package com.eventsystem.application.order;

import com.eventsystem.application.appexceptions.AlreadyExistsOrderException;
import com.eventsystem.application.appexceptions.OrderNotFoundException;
import com.eventsystem.application.event.IZoneServicePort;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.ILotteryRepository;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.IActiveOrderRepository;
import com.eventsystem.domain.order.OrderFactory;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.ZoneType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Transactional
class OrderServiceTest {

    @Mock
    private IActiveOrderRepository orderRepository;
    @Mock
    private IZoneServicePort zoneService;
    @Mock
    private OrderFactory orderFactory;
    @Mock
    private ILotteryRepository lotteryRepository;
    @Mock
    private IZoneRepository zoneRepository;

    @InjectMocks
    private OrderService orderService;

    private final String EVENT_ID = "EVENT-123";
    private final String ORDER_ID = "ORDER-123";
    private BuyerReference testBuyer;
    private ActiveOrder testOrder;

    @BeforeEach
    void setUp() {
        testBuyer = new BuyerReference(BuyerType.MEMBER, "sess-1", "member-123");
        testOrder = mock(ActiveOrder.class);
        lenient().when(testOrder.getOrderId()).thenReturn(ORDER_ID);
        lenient().when(testOrder.getBuyerRef()).thenReturn(testBuyer);
        lenient().when(testOrder.getEventId()).thenReturn(EVENT_ID);
        lenient().when(testOrder.getStatus()).thenReturn(com.eventsystem.domain.order.OrderStatus.ACTIVE);
        lenient().when(testOrder.isExpired()).thenReturn(false);
    }

    // ==========================================
    // createOrGetActiveOrder
    // ==========================================

    @Test
    void createOrGetActiveOrder_OrderExists_ReturnsExistingOrder() {
        when(orderRepository.findByBuyerAndEvent(testBuyer, EVENT_ID)).thenReturn(Optional.of(testOrder));

        ActiveOrderDTO result = orderService.createOrGetActiveOrder(testBuyer, EVENT_ID, Optional.empty());

        assertEquals(ORDER_ID, result.orderId());
        verify(orderRepository, never()).save(any()); // לא שומרים מחדש
    }

    @Test
    void createOrGetActiveOrder_NoOrderNoLottery_CreatesNewOrder() {
        when(orderRepository.findByBuyerAndEvent(testBuyer, EVENT_ID)).thenReturn(Optional.empty());
        when(lotteryRepository.findByEventId(new EventId(EVENT_ID))).thenReturn(Optional.empty());
        when(orderFactory.createOrder(eq(testBuyer), eq(EVENT_ID), any())).thenReturn(testOrder);

        ActiveOrderDTO result = orderService.createOrGetActiveOrder(testBuyer, EVENT_ID, Optional.empty());

        assertEquals(ORDER_ID, result.orderId());
        verify(orderRepository).save(testOrder);
    }

    @Test
    void createOrGetActiveOrder_LotteryExistsValidCode_CreatesNewOrder() {
        Lottery mockLottery = mock(Lottery.class);
        when(orderRepository.findByBuyerAndEvent(testBuyer, EVENT_ID)).thenReturn(Optional.empty());
        when(lotteryRepository.findByEventId(new EventId(EVENT_ID))).thenReturn(Optional.of(mockLottery));
        when(mockLottery.validateCode(eq("VALID-CODE"), any(Instant.class))).thenReturn(Optional.of(new MemberId("member-123")));
        when(orderFactory.createOrder(eq(testBuyer), eq(EVENT_ID), any())).thenReturn(testOrder);

        ActiveOrderDTO result = orderService.createOrGetActiveOrder(testBuyer, EVENT_ID, Optional.of("VALID-CODE"));

        assertEquals(ORDER_ID, result.orderId());
        verify(orderRepository).save(testOrder);
    }

    @Test
    void createOrGetActiveOrder_LotteryExistsInvalidCode_ThrowsSecurityException() {
        Lottery mockLottery = mock(Lottery.class);
        when(lotteryRepository.findByEventId(new EventId(EVENT_ID))).thenReturn(Optional.of(mockLottery));
        when(mockLottery.validateCode(eq("INVALID-CODE"), any(Instant.class))).thenReturn(Optional.empty());

        assertThrows(SecurityException.class, () -> 
            orderService.createOrGetActiveOrder(testBuyer, EVENT_ID, Optional.of("INVALID-CODE"))
        );
        verify(orderRepository, never()).save(any());
    }

    // ==========================================
    // createNewOrderStrict
    // ==========================================

    @Test
    void createNewOrderStrict_OrderAlreadyExists_ThrowsException() {
        when(orderRepository.findByBuyerAndEvent(testBuyer, EVENT_ID)).thenReturn(Optional.of(testOrder));

        assertThrows(AlreadyExistsOrderException.class, () -> 
            orderService.createNewOrderStrict(testBuyer, EVENT_ID)
        );
    }

    @Test
    void createNewOrderStrict_NoOrderExists_CreatesNewOrder() {
        when(orderRepository.findByBuyerAndEvent(testBuyer, EVENT_ID)).thenReturn(Optional.empty());
        when(orderFactory.createOrder(eq(testBuyer), eq(EVENT_ID), any())).thenReturn(testOrder);

        ActiveOrderDTO result = orderService.createNewOrderStrict(testBuyer, EVENT_ID);

        assertEquals(ORDER_ID, result.orderId());
        verify(orderRepository).save(testOrder);
    }

    // ==========================================
    // addItemToOrder & removeItemFromOrder
    // ==========================================

    @Test
    void addItemToOrder_OrderNotFound_ThrowsException() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> 
            orderService.addItemToOrder(ORDER_ID, "ZONE-1", "SEAT-1", 1)
        );
    }

    @Test
    void addItemToOrder_OrderExists_AddsItemAndSaves() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));

        orderService.addItemToOrder(ORDER_ID, "ZONE-1", "SEAT-1", 2);

        // משתמשים ב-any() שמסכים לקבל את ה-null שמגיע מהמוק של zoneService
        verify(testOrder).addItem(any());
        verify(orderRepository).save(testOrder);
    }

    @Test
    void removeItemFromOrder_OrderExists_RemovesItemAndSaves() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));

        orderService.removeItemFromOrder(ORDER_ID, "ZONE-1", "SEAT-1", 1);

        verify(testOrder).removeItem("ZONE-1", "SEAT-1");
        verify(orderRepository).save(testOrder);
    }

    // ==========================================
    // getOrderById
    // ==========================================

    @Test
    void getOrderById_Found_ReturnsDTO() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));
        
        ActiveOrderDTO result = orderService.getOrderById(ORDER_ID);
        
        assertEquals(ORDER_ID, result.orderId());
    }

    @Test
    void getOrderById_NotFound_ThrowsException() {
        when(orderRepository.findById("BOGUS-ID")).thenReturn(Optional.empty());
        
        assertThrows(OrderNotFoundException.class, () -> 
            orderService.getOrderById("BOGUS-ID")
        );
    }

    // ==========================================
    // sweepExpiredOrders (Complex Logic with Locks)
    // ==========================================

    @Test
    void sweepExpiredOrders_NoOrders_DoesNothing() {
        when(orderRepository.findExpired()).thenReturn(Optional.of(List.of()));
        
        orderService.sweepExpiredOrders();
        
        verify(zoneRepository, never()).withLock(any(), any());
    }

    @Test
    void sweepExpiredOrders_WithExpiredOrders_ReleasesSeatsAndSaves() {
        // Arrange
        when(orderRepository.findExpired()).thenReturn(Optional.of(List.of(testOrder)));
        
        // יצירת פריטי הזמנה פגי תוקף: אחד עמידה, אחד ישיבה
        OrderItem standingItem = mock(OrderItem.class);
        when(standingItem.getZoneId()).thenReturn("ZONE-STAND");
        when(standingItem.getQuantity()).thenReturn(5);
        
        OrderItem seatedItem = mock(OrderItem.class);
        when(seatedItem.getZoneId()).thenReturn("ZONE-SEAT");
        when(seatedItem.getSeatId()).thenReturn("SEAT-1");
        
        when(testOrder.expire()).thenReturn(List.of(standingItem, seatedItem));
        
        // התנהגות ה-Zone Repository
        Zone standingZone = mock(Zone.class);
        when(standingZone.zoneType()).thenReturn(ZoneType.STANDING);
        Zone seatedZone = mock(Zone.class);
        when(seatedZone.zoneType()).thenReturn(ZoneType.SEATED);
        
        when(zoneRepository.findById(new ZoneId("ZONE-STAND"))).thenReturn(Optional.of(standingZone));
        when(zoneRepository.findById(new ZoneId("ZONE-SEAT"))).thenReturn(Optional.of(seatedZone));
        
        // קסם המוק - גורם לפונקציית ה-withLock להריץ את ה-Runnable מיד!
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(zoneRepository).withLock(any(ZoneId.class), any(Runnable.class));

        // Act
        orderService.sweepExpiredOrders();

        // Assert
        verify(testOrder).expire();
        verify(orderRepository).save(testOrder);
        
        // וידוא שחרור מקומות
        verify(standingZone).releaseStanding(5);
        verify(seatedZone).releaseSeat(new SeatId("SEAT-1"));
        
        // וידוא שמירה של האזורים
        verify(zoneRepository).save(standingZone);
        verify(zoneRepository).save(seatedZone);
    }
}