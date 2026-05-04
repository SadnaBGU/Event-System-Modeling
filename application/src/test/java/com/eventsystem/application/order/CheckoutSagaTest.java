package com.eventsystem.application.order;

import com.eventsystem.application.event.EventQueryPort;
import com.eventsystem.application.event.ZoneServicePort;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderFactory;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutSagaTest {

    // Here we declare all the dependencies of the CheckoutSaga as Mocks
    @Mock private ActiveOrderRepository orderRepository;
    @Mock private PurchaseRecordRepository purchaseRecordRepository;
    @Mock private PaymentGatewayPort paymentGateway;
    @Mock private TicketIssuancePort ticketIssuance;
    @Mock private NotificationPort notificationPort;
    @Mock private ZoneServicePort zoneService;
    @Mock private EventQueryPort eventQueryPort;

    // This is the class we are testing, and we want to inject the Mocks into it
    @InjectMocks private CheckoutSaga checkoutSaga;

    private ActiveOrder testOrder;
    private BuyerReference testBuyer;
    private final String ORDER_ID = "ORDER-123";
    private final String EVENT_ID = "EVENT-456";

    @BeforeEach
    void setUp() {
        testBuyer = new BuyerReference(BuyerType.MEMBER, "sess-1", "member-99");
        OrderFactory factory = new OrderFactory();
        testOrder = factory.createOrder(testBuyer, EVENT_ID, Instant.now().plus(10, ChronoUnit.MINUTES));
        
        // Adding a mock item to the order to ensure it's not empty during checkout
        OrderItem mockItem = new OrderItem("VIP-ZONE", "SEAT-42", 1, new BigDecimal("150.00"));
        testOrder.addItem(mockItem);

        // Define the behavior of the orderRepository mock to return our testOrder when findById is called with ORDER_ID
        lenient().when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));
    }

    @Test
    void executeCheckout_HappyPath_CompletesSuccessfully() {
        // Arrange - defining the behavior of all the mocks to simulate a successful checkout process
        when(eventQueryPort.validatePurchasePolicy(eq(EVENT_ID), eq(testBuyer), any()))
                .thenReturn(true);
        
        when(eventQueryPort.applyDiscount(eq(EVENT_ID), any(), any()))
                .thenReturn(new DiscountSnapshot("No Discount", BigDecimal.ZERO));
                
        when(paymentGateway.charge(eq(ORDER_ID), any(), eq(testBuyer), any()))
                .thenReturn(PaymentResult.successful("TXN-777"));
                
        when(ticketIssuance.issueTickets(eq(EVENT_ID), eq(ORDER_ID), any(), eq(testBuyer)))
                .thenReturn(IssuanceResult.successful("TICKET-888"));
                
        when(eventQueryPort.getEventSnapshot(EVENT_ID))
                .thenReturn(new EventSnapshot(EVENT_ID, "Rock Concert", "LiveNation", LocalDate.now(), "Park"));

        // Act
        checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");

        // Assert
        verify(purchaseRecordRepository, times(1)).append(any());
        verify(orderRepository, times(1)).save(testOrder);
        verify(notificationPort, times(1)).sendPurchaseSuccess(eq(testBuyer), any());
    }

    @Test
    void executeCheckout_TicketIssuanceFails_TriggersCompensatingAction() {
        // Arrange
        when(eventQueryPort.validatePurchasePolicy(any(), any(), any())).thenReturn(true);
        when(eventQueryPort.applyDiscount(any(), any(), any())).thenReturn(new DiscountSnapshot("No Discount", BigDecimal.ZERO));
        
        when(paymentGateway.charge(any(), any(), any(), any()))
                .thenReturn(PaymentResult.successful("TXN-777"));
                
        when(ticketIssuance.issueTickets(any(), any(), any(), any()))
                .thenReturn(IssuanceResult.failed("Printer out of ink"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });

        verify(paymentGateway, times(1)).refund(eq("TXN-777"), any(), anyString());
        verify(notificationPort, times(1)).sendPurchaseFailure(eq(testBuyer), anyString());
        verify(purchaseRecordRepository, never()).append(any());

        verify(zoneService, times(1)).unlockSeat("VIP-ZONE", "SEAT-42");
    }

    @Test
    void executeCheckout_OrderNotFound_ThrowsException() {
        // Arrange
        when(orderRepository.findById("INVALID_ORDER")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            checkoutSaga.executeCheckout("INVALID_ORDER", "VALID_TOKEN", "DISCOUNT10");
        });
        assertEquals("Order not found", exception.getMessage());
    }

    @Test
    void executeCheckout_OrderExpired_ThrowsException() {
        // Arrange
        ActiveOrder expiredOrder = mock(ActiveOrder.class);
        when(expiredOrder.isExpired()).thenReturn(true);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(expiredOrder));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });
        assertEquals("Order reservation timer expired", exception.getMessage());
    }

    @Test
    void executeCheckout_PolicyViolation_ThrowsException() {
        // Arrange
        when(eventQueryPort.validatePurchasePolicy(any(), any(), any())).thenReturn(false);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });
        assertEquals("Order violates purchase policy", exception.getMessage());
        
        verify(paymentGateway, never()).charge(any(), any(), any(), any());
    }

    @Test
    void executeCheckout_PaymentDeclined_ThrowsException() {
        // Arrange
        when(eventQueryPort.validatePurchasePolicy(any(), any(), any())).thenReturn(true);
        when(eventQueryPort.applyDiscount(any(), any(), any())).thenReturn(new DiscountSnapshot("None", BigDecimal.ZERO));
        
        when(paymentGateway.charge(any(), any(), any(), any()))
                .thenReturn(PaymentResult.failed("Insufficient funds"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "INVALID_TOKEN", "DISCOUNT10");
        });
        
        assertTrue(exception.getMessage().contains("Payment failed"));
        
        verify(notificationPort).sendPurchaseFailure(eq(testBuyer), eq("Payment declined"));
        verify(ticketIssuance, never()).issueTickets(any(), any(), any(), any());
    }

    @Test
    void executeCheckout_IssuanceThrowsException_TriggersCrashCompensation() {
        // Arrange
        when(eventQueryPort.validatePurchasePolicy(any(), any(), any())).thenReturn(true);
        when(eventQueryPort.applyDiscount(any(), any(), any())).thenReturn(new DiscountSnapshot("None", BigDecimal.ZERO));
        
        when(paymentGateway.charge(any(), any(), any(), any()))
                .thenReturn(PaymentResult.successful("TXN-999"));
                
        when(ticketIssuance.issueTickets(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Connection Timeout 504"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });
        
        assertTrue(exception.getMessage().contains("automatic refund triggered"));

        verify(paymentGateway).refund(eq("TXN-999"), any(), contains("Ticket issuance service crashed"));
        verify(notificationPort).sendPurchaseFailure(eq(testBuyer), contains("System error"));
        
        verify(zoneService, times(1)).unlockSeat("VIP-ZONE", "SEAT-42");
    }
}