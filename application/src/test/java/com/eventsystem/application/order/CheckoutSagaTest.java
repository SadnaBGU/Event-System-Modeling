package com.eventsystem.application.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eventsystem.application.appexceptions.ActiveOrderHasExpiredException;
import com.eventsystem.application.appexceptions.OrderNotFoundException;
import com.eventsystem.application.appexceptions.OrderViolatesPolicyException;
import com.eventsystem.application.appexceptions.PaymentFailedException;
import com.eventsystem.application.appexceptions.PriceCalcException;
import com.eventsystem.application.event.IEventQueryPort;
import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.application.policy.IDiscountApplicationPort;
import com.eventsystem.application.policy.IPurchasePolicyValidationPort;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.IActiveOrderRepository;
import com.eventsystem.domain.order.OrderFactory;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.purchaserecord.IPurchaseRecordRepository;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;

import com.eventsystem.application.appexceptions.IssuanceFailedException;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;

@ExtendWith(MockitoExtension.class)
class CheckoutSagaTest {

    // Here we declare all the dependencies of the CheckoutSaga as Mocks
    @Mock private IActiveOrderRepository orderRepository;
    @Mock private IPurchaseRecordRepository purchaseRecordRepository;
    @Mock private IPaymentGatewayPort paymentGateway;
    @Mock private ITicketIssuancePort ticketIssuance;
    @Mock private INotificationPort notificationPort;
    @Mock private IZoneRepository zoneRepository;
    @Mock private IEventQueryPort eventQueryPort;
    @Mock private IPurchasePolicyValidationPort purchasePolicyPort;
    @Mock private IDiscountApplicationPort discountPort;

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
        OrderItem mockItem = new OrderItem("VIP-ZONE", "SEAT-42", 1, Money.of(BigDecimal.valueOf(150), "USD"));
        testOrder.addItem(mockItem);

        // Define the behavior of the orderRepository mock to return our testOrder when findById is called with ORDER_ID
        lenient().when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));
    }

    private void mockWithLockExecution() {
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(zoneRepository).withLock(any(ZoneId.class), any(Runnable.class));
    }

    private PolicyValidationResult successResult = new PolicyValidationResult(true, null);
    private PolicyValidationResult failResult = new PolicyValidationResult(false, "FAIL");

    private PurchaseContext purchaseContext() {
        return new PurchaseContext(
                new EventId(EVENT_ID),
                new CompanyId("company-1"),
                List.of(new ZoneId("VIP-ZONE")),
                LocalDate.now().minusYears(25),
                null
        );
    }

    private void mockPurchaseContextCreation() {
        when(purchasePolicyPort.createPurchaseContext(any(EventId.class), any(BuyerReference.class), any()))
                .thenReturn(purchaseContext());
    }

    private void mockSuccessfulPolicyValidation() {
        mockPurchaseContextCreation();
        when(purchasePolicyPort.evaluatePurchasePolicyFor(any(PurchaseContext.class)))
                .thenReturn(successResult);
    }

    private void mockNoDiscount() {
        when(discountPort.generateDiscountSnapshot(any(PurchaseContext.class), any(Money.class)))
                .thenReturn(new DiscountSnapshot("No Discount", Money.of(BigDecimal.ZERO, "USD")));
    }

    @Test
    void executeCheckout_HappyPath_CompletesSuccessfully() {
        // Arrange - defining the behavior of all the mocks to simulate a successful checkout process
        mockSuccessfulPolicyValidation();
        mockNoDiscount();
                
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
        mockSuccessfulPolicyValidation();
        mockNoDiscount();

        when(paymentGateway.charge(any(), any(), any(), any()))
                .thenReturn(PaymentResult.successful("TXN-777"));
                
        when(ticketIssuance.issueTickets(any(), any(), any(), any()))
                .thenReturn(IssuanceResult.failed("Printer out of ink"));

        mockWithLockExecution();
        Zone mockZone = mock(Zone.class);
        when(zoneRepository.findById(new ZoneId("VIP-ZONE"))).thenReturn(Optional.of(mockZone));
        

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });

        verify(paymentGateway, times(1)).refund(eq("TXN-777"), any(), anyString());
        verify(notificationPort, times(1)).sendPurchaseFailure(eq(testBuyer), anyString());
        verify(purchaseRecordRepository, never()).append(any());

        verify(mockZone, times(1)).releaseSeat(new SeatId("SEAT-42"));
        verify(zoneRepository, times(1)).save(mockZone);
    }

    @Test
    void executeCheckout_OrderNotFound_ThrowsException() {
        // Arrange
        when(orderRepository.findById("INVALID_ORDER")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(OrderNotFoundException.class, () -> {
            checkoutSaga.executeCheckout("INVALID_ORDER", "VALID_TOKEN", "DISCOUNT10");
        });
    }

    @Test
    void executeCheckout_OrderExpired_ThrowsException() {
        // Arrange
        ActiveOrder expiredOrder = mock(ActiveOrder.class);
        when(expiredOrder.isExpired()).thenReturn(true);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(expiredOrder));

        // Act & Assert
        assertThrows(ActiveOrderHasExpiredException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });
    }

    @Test
    void executeCheckout_PolicyViolation_ThrowsException() {
        // Arrange
        when(purchasePolicyPort.evaluatePurchasePolicyFor(any())).thenReturn(failResult);

        // Act & Assert
        assertThrows(OrderViolatesPolicyException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });
        
        verify(paymentGateway, never()).charge(any(), any(), any(), any());
    }

    @Test
    void executeCheckout_PaymentDeclined_ThrowsException() {
        // Arrange
        mockSuccessfulPolicyValidation();
        mockNoDiscount();

        when(paymentGateway.charge(any(), any(), any(), any()))
                .thenReturn(PaymentResult.failed("Insufficient funds"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "INVALID_TOKEN", "DISCOUNT10");
        });

        verify(notificationPort).sendPurchaseFailure(eq(testBuyer), eq("Payment declined"));
        verify(ticketIssuance, never()).issueTickets(any(), any(), any(), any());
    }

    @Test
    void executeCheckout_IssuanceThrowsException_TriggersCrashCompensation() {
        // Arrange
        mockSuccessfulPolicyValidation();
        mockNoDiscount();

        when(paymentGateway.charge(any(), any(), any(), any()))
                .thenReturn(PaymentResult.successful("TXN-999"));
                
        when(ticketIssuance.issueTickets(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Connection Timeout 504"));


        mockWithLockExecution();
        Zone mockZone = mock(Zone.class);
        when(zoneRepository.findById(new ZoneId("VIP-ZONE"))).thenReturn(Optional.of(mockZone));
        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });
        
        verify(paymentGateway).refund(eq("TXN-999"), any(), contains("Ticket issuance service crashed"));
        verify(notificationPort).sendPurchaseFailure(eq(testBuyer), contains("System error"));
        
        verify(mockZone, times(1)).releaseSeat(new SeatId("SEAT-42"));
        verify(zoneRepository, times(1)).save(mockZone);
    }

    @Test
    void executeCheckout_PaymentGatewayTimeout_AbortsCheckout() {
        // Arrange
        mockSuccessfulPolicyValidation();
        mockNoDiscount();

        when(paymentGateway.charge(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Payment Gateway Timeout"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });
        
        // Assert we never tried to issue tickets or append records
        verify(ticketIssuance, never()).issueTickets(any(), any(), any(), any());
        verify(purchaseRecordRepository, never()).append(any());
        // Verify payment gateway was NOT asked for a refund because the charge itself timed out
        verify(paymentGateway, never()).refund(any(), any(), any());
    }

    @Test
    void executeCheckout_PolicyValidationThrows_MapsToPolicyViolationException() {
        // Arrange
        when(purchasePolicyPort.evaluatePurchasePolicyFor(any()))
                .thenThrow(new RuntimeException("Policy subsystem unavailable"));

        // Act & Assert
        assertThrows(OrderViolatesPolicyException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });

        verify(paymentGateway, never()).charge(any(), any(), any(), any());
        verify(purchaseRecordRepository, never()).append(any());
    }

    @Test
    void executeCheckout_DiscountEvaluationFails_ThrowsPriceCalcException() {
        // Arrange
        mockSuccessfulPolicyValidation();
        when(discountPort.generateDiscountSnapshot(any(), any()))
                .thenThrow(new RuntimeException("Discount service down"));

        // Act & Assert
        assertThrows(PriceCalcException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });

        verify(paymentGateway, never()).charge(any(), any(), any(), any());
        verify(ticketIssuance, never()).issueTickets(any(), any(), any(), any());
        verify(purchaseRecordRepository, never()).append(any());
    }

    @Test
    void executeCheckout_PaymentGatewayThrows_ThrowsPaymentFailedExceptionAndNotifies() {
        // Arrange
        mockSuccessfulPolicyValidation();
        mockNoDiscount();
        
        when(paymentGateway.charge(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Gateway unavailable"));

        // Act & Assert
        assertThrows(PaymentFailedException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });

        verify(notificationPort).sendPurchaseFailure(eq(testBuyer), contains("Payment processing error"));
        verify(ticketIssuance, never()).issueTickets(any(), any(), any(), any());
        verify(paymentGateway, never()).refund(any(), any(), any());
        verify(purchaseRecordRepository, never()).append(any());
    }
}