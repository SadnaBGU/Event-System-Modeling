package com.eventsystem.application.order;

import com.eventsystem.application.TestPurchaseContexts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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

import org.springframework.transaction.annotation.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eventsystem.application.appexceptions.ActiveOrderHasExpiredException;
import com.eventsystem.application.appexceptions.IssuanceFailedException;
import com.eventsystem.application.appexceptions.OrderNotFoundException;
import com.eventsystem.application.appexceptions.OrderViolatesPolicyException;
import com.eventsystem.application.appexceptions.PaymentFailedException;
import com.eventsystem.application.appexceptions.PriceCalcException;
import com.eventsystem.application.event.IEventQueryPort;
import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.application.policy.IDiscountApplicationPort;
import com.eventsystem.application.policy.IPurchasePolicyValidationPort;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.IActiveOrderRepository;
import com.eventsystem.domain.order.OrderFactory;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.order.OrderStatus;
import com.eventsystem.domain.policy.discount.DiscountSummary;
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
import com.eventsystem.domain.zone.ZoneType;

@ExtendWith(MockitoExtension.class)
@Transactional
class CheckoutSagaTest {

    @Mock
    private IActiveOrderRepository orderRepository;
    @Mock
    private IPurchaseRecordRepository purchaseRecordRepository;
    @Mock
    private IPaymentGatewayPort paymentGateway;
    @Mock
    private ITicketIssuancePort ticketIssuance;
    @Mock
    private INotificationPort notificationPort;
    @Mock
    private IZoneRepository zoneRepository;
    @Mock
    private IEventQueryPort eventQueryPort;
    @Mock
    private IPurchasePolicyValidationPort purchasePolicyPort;
    @Mock
    private IDiscountApplicationPort discountPort;

    @InjectMocks
    private CheckoutSaga checkoutSaga;

    private ActiveOrder testOrder;
    private BuyerReference testBuyer;

    private static final String ORDER_ID = "ORDER-123";
    private static final String EVENT_ID = "EVENT-456";

    @BeforeEach
    void setUp() {
        testBuyer = new BuyerReference(BuyerType.MEMBER, "sess-1", "member-99");

        OrderFactory factory = new OrderFactory();
        testOrder = factory.createOrder(testBuyer, EVENT_ID, Instant.now().plus(10, ChronoUnit.MINUTES));

        OrderItem mockItem = new OrderItem(
                "VIP-ZONE",
                "SEAT-42",
                1,
                Money.of(BigDecimal.valueOf(150), "USD"));
        testOrder.addItem(mockItem);

        lenient().when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));
    }

    private void mockWithLockExecution() {
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(zoneRepository).withLock(any(ZoneId.class), any(Runnable.class));
    }

    private PurchaseContext purchaseContext() {
        return TestPurchaseContexts.contextWithZoneSubtotal(
                new EventId(EVENT_ID),
                new CompanyId("company-1"),
                new ZoneId("VIP-ZONE"),
                1,
                "150.00");
    }

    private void mockPurchaseContextCreation() {
        when(purchasePolicyPort.createPurchaseContext(any(EventId.class), any(BuyerReference.class), any()))
                .thenReturn(purchaseContext());
    }

    private void mockSuccessfulPolicyValidation() {
        mockPurchaseContextCreation();
        when(purchasePolicyPort.evaluatePurchasePolicyFor(any(PurchaseContext.class)))
                .thenReturn(new PolicyValidationResult(true, null));
    }

    private void mockNoDiscount() {
        when(discountPort.calculateDiscountSummary(any(PurchaseContext.class), any(Money.class)))
                .thenReturn(DiscountSummary.noDiscountSummary());

        when(discountPort.discountSnapshotFromSummary(any(DiscountSummary.class), any(Money.class)))
                .thenReturn(new DiscountSnapshot("No Discount", Money.of(BigDecimal.ZERO, "USD")));
    }

    private Zone mockSeatedZone(String zoneId) {
        mockWithLockExecution();

        Zone mockZone = mock(Zone.class);
        when(mockZone.zoneType()).thenReturn(ZoneType.SEATED);
        when(zoneRepository.findById(new ZoneId(zoneId))).thenReturn(Optional.of(mockZone));

        return mockZone;
    }

    private Zone mockStandingZone(String zoneId) {
        mockWithLockExecution();

        Zone mockZone = mock(Zone.class);
        when(mockZone.zoneType()).thenReturn(ZoneType.STANDING);
        when(zoneRepository.findById(new ZoneId(zoneId))).thenReturn(Optional.of(mockZone));

        return mockZone;
    }

    @Test
    void executeCheckout_HappyPath_CompletesSuccessfullyAndReturnsTicketCodes() {
        // REQ: INV-10, UC 9, UAT-26
        mockSuccessfulPolicyValidation();
        mockNoDiscount();

        when(paymentGateway.charge(eq(ORDER_ID), any(), eq(testBuyer), any()))
                .thenReturn(PaymentResult.successful("TXN-777"));

        when(ticketIssuance.issueTickets(eq(EVENT_ID), eq(ORDER_ID), any(), eq(testBuyer)))
                .thenReturn(IssuanceResult.successful("TICKET-888"));

        when(eventQueryPort.getEventSnapshot(EVENT_ID))
                .thenReturn(new EventSnapshot(EVENT_ID, "Rock Concert", "LiveNation", LocalDate.now(), "Park"));

        Zone mockZone = mockSeatedZone("VIP-ZONE");

        CheckoutResult result = checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");

        verify(mockZone, times(1)).markSold(new SeatId("SEAT-42"));
        verify(zoneRepository, times(1)).save(mockZone);
        verify(purchaseRecordRepository, times(1)).append(any());
        verify(orderRepository, times(1)).save(testOrder);
        verify(notificationPort, times(1)).sendPurchaseSuccess(eq(testBuyer), any());

        assertEquals(ORDER_ID, result.orderId());
        assertEquals(OrderStatus.CHECKED_OUT.name(), result.orderStatus());
        assertEquals("TXN-777", result.paymentTransactionId());
        assertEquals(List.of("TICKET-888"), result.issuedTicketCodes());
        assertEquals(OrderStatus.CHECKED_OUT, testOrder.getStatus());
    }

    @Test
    void executeCheckout_HappyPath_ForStandingZone_MarksStandingInventorySold() {
        // REQ: INV-10, UC 9, UAT-26
        mockSuccessfulPolicyValidation();
        mockNoDiscount();

        ActiveOrder standingOrder = new ActiveOrder(
                "ORDER-STANDING-SUCCESS",
                testBuyer,
                EVENT_ID,
                Instant.now().plus(10, ChronoUnit.MINUTES));
        standingOrder.addItem(new OrderItem(
                "STANDING-ZONE",
                null,
                3,
                Money.of(BigDecimal.valueOf(50), "USD")));

        when(orderRepository.findById("ORDER-STANDING-SUCCESS")).thenReturn(Optional.of(standingOrder));

        when(paymentGateway.charge(eq("ORDER-STANDING-SUCCESS"), any(), eq(testBuyer), any()))
                .thenReturn(PaymentResult.successful("TXN-STANDING-SUCCESS"));

        when(ticketIssuance.issueTickets(eq(EVENT_ID), eq("ORDER-STANDING-SUCCESS"), any(), eq(testBuyer)))
                .thenReturn(IssuanceResult.successful(List.of("TIX-1", "TIX-2", "TIX-3")));

        when(eventQueryPort.getEventSnapshot(EVENT_ID))
                .thenReturn(new EventSnapshot(EVENT_ID, "Rock Concert", "LiveNation", LocalDate.now(), "Park"));

        Zone mockZone = mockStandingZone("STANDING-ZONE");

        CheckoutResult result = checkoutSaga.executeCheckout("ORDER-STANDING-SUCCESS", "VALID_TOKEN", null);

        verify(mockZone, times(1)).markSoldStanding(3);
        verify(zoneRepository, times(1)).save(mockZone);
        verify(purchaseRecordRepository, times(1)).append(any());
        verify(orderRepository, times(1)).save(standingOrder);
        verify(notificationPort, times(1)).sendPurchaseSuccess(eq(testBuyer), any());

        assertEquals(OrderStatus.CHECKED_OUT.name(), result.orderStatus());
        assertEquals(List.of("TIX-1", "TIX-2", "TIX-3"), result.issuedTicketCodes());
        assertEquals(OrderStatus.CHECKED_OUT, standingOrder.getStatus());
    }

    @Test
    void executeCheckout_TicketIssuanceFails_TriggersRefundReleaseCancelAndNoReceipt() {
        // REQ: INV-10, ROB-01, UC 9, UAT-30
        mockSuccessfulPolicyValidation();
        mockNoDiscount();

        when(paymentGateway.charge(any(), any(), any(), any()))
                .thenReturn(PaymentResult.successful("TXN-777"));

        when(ticketIssuance.issueTickets(any(), any(), any(), any()))
                .thenReturn(IssuanceResult.failed("Printer out of ink"));

        Zone mockZone = mockSeatedZone("VIP-ZONE");

        assertThrows(IssuanceFailedException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });

        verify(paymentGateway, times(1)).refund(eq("TXN-777"), any(), contains("Ticket issuance failed"));
        verify(notificationPort, times(1)).sendPurchaseFailure(eq(testBuyer), contains("Technical error"));
        verify(purchaseRecordRepository, never()).append(any());

        verify(mockZone, times(1)).releaseSeat(new SeatId("SEAT-42"));
        verify(zoneRepository, times(1)).save(mockZone);
        verify(orderRepository, times(1)).save(testOrder);

        assertEquals(OrderStatus.CANCELLED, testOrder.getStatus());
    }

    @Test
    void executeCheckout_TicketIssuanceFails_ForStandingZone_ReleasesStandingQuantityAndCancelsOrder() {
        // REQ: INV-10, ROB-01, UC 9, UAT-30
        mockSuccessfulPolicyValidation();
        mockNoDiscount();

        ActiveOrder standingOrder = new ActiveOrder(
                "ORDER-STANDING-FAIL",
                testBuyer,
                EVENT_ID,
                Instant.now().plus(10, ChronoUnit.MINUTES));
        standingOrder.addItem(new OrderItem(
                "STANDING-ZONE",
                null,
                3,
                Money.of(BigDecimal.valueOf(50), "USD")));

        when(orderRepository.findById("ORDER-STANDING-FAIL")).thenReturn(Optional.of(standingOrder));

        when(paymentGateway.charge(eq("ORDER-STANDING-FAIL"), any(), eq(testBuyer), any()))
                .thenReturn(PaymentResult.successful("TXN-STANDING-FAIL"));

        when(ticketIssuance.issueTickets(eq(EVENT_ID), eq("ORDER-STANDING-FAIL"), any(), eq(testBuyer)))
                .thenReturn(IssuanceResult.failed("Supply service unavailable"));

        Zone mockZone = mockStandingZone("STANDING-ZONE");

        assertThrows(IssuanceFailedException.class, () -> {
            checkoutSaga.executeCheckout("ORDER-STANDING-FAIL", "VALID_TOKEN", null);
        });

        verify(paymentGateway, times(1)).refund(eq("TXN-STANDING-FAIL"), any(), contains("Ticket issuance failed"));
        verify(mockZone, times(1)).releaseStanding(3);
        verify(zoneRepository, times(1)).save(mockZone);
        verify(orderRepository, times(1)).save(standingOrder);
        verify(purchaseRecordRepository, never()).append(any());
        verify(notificationPort, times(1)).sendPurchaseFailure(eq(testBuyer), contains("Technical error"));

        assertEquals(OrderStatus.CANCELLED, standingOrder.getStatus());
    }

    @Test
    void executeCheckout_OrderNotFound_ThrowsException() {
        when(orderRepository.findById("INVALID_ORDER")).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> {
            checkoutSaga.executeCheckout("INVALID_ORDER", "VALID_TOKEN", "DISCOUNT10");
        });
    }

    @Test
    void executeCheckout_OrderExpired_ThrowsException() {
        ActiveOrder expiredOrder = mock(ActiveOrder.class);
        when(expiredOrder.isExpired()).thenReturn(true);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(expiredOrder));

        assertThrows(ActiveOrderHasExpiredException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });
    }

    @Test
    void executeCheckout_PolicyViolation_ThrowsException() {
        mockPurchaseContextCreation();
        when(purchasePolicyPort.evaluatePurchasePolicyFor(any()))
                .thenReturn(new PolicyValidationResult(false, "FAIL"));

        assertThrows(OrderViolatesPolicyException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });

        verify(paymentGateway, never()).charge(any(), any(), any(), any());
    }

    @Test
    void executeCheckout_PaymentDeclined_ThrowsException() {
        mockSuccessfulPolicyValidation();
        mockNoDiscount();

        when(paymentGateway.charge(any(), any(), any(), any()))
                .thenReturn(PaymentResult.failed("Insufficient funds"));

        assertThrows(PaymentFailedException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "INVALID_TOKEN", "DISCOUNT10");
        });

        // The purchase-failure notification now carries the specific decline reason
        // rather than a generic "Payment declined".
        verify(notificationPort).sendPurchaseFailure(eq(testBuyer), eq("Insufficient funds"));
        verify(ticketIssuance, never()).issueTickets(any(), any(), any(), any());
        verify(purchaseRecordRepository, never()).append(any());
    }

    @Test
    void executeCheckout_IssuanceThrowsException_TriggersRefundReleaseCancelAndNoReceipt() {
        // REQ: INV-10, ROB-01, UC 9, UAT-29/UAT-30
        mockSuccessfulPolicyValidation();
        mockNoDiscount();

        when(paymentGateway.charge(any(), any(), any(), any()))
                .thenReturn(PaymentResult.successful("TXN-999"));

        when(ticketIssuance.issueTickets(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Connection Timeout 504"));

        Zone mockZone = mockSeatedZone("VIP-ZONE");

        assertThrows(IssuanceFailedException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });

        verify(paymentGateway).refund(eq("TXN-999"), any(), contains("Ticket issuance service failed"));
        verify(notificationPort).sendPurchaseFailure(eq(testBuyer), contains("Technical error"));
        verify(purchaseRecordRepository, never()).append(any());

        verify(mockZone, times(1)).releaseSeat(new SeatId("SEAT-42"));
        verify(zoneRepository, times(1)).save(mockZone);
        verify(orderRepository, times(1)).save(testOrder);

        assertEquals(OrderStatus.CANCELLED, testOrder.getStatus());
    }

    @Test
    void executeCheckout_PaymentGatewayTimeout_AbortsCheckout() {
        mockSuccessfulPolicyValidation();
        mockNoDiscount();

        when(paymentGateway.charge(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Payment Gateway Timeout"));

        assertThrows(PaymentFailedException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });

        verify(ticketIssuance, never()).issueTickets(any(), any(), any(), any());
        verify(purchaseRecordRepository, never()).append(any());
        verify(paymentGateway, never()).refund(any(), any(), any());
    }

    @Test
    void executeCheckout_PolicyValidationThrows_MapsToPolicyViolationException() {
        mockPurchaseContextCreation();
        when(purchasePolicyPort.evaluatePurchasePolicyFor(any()))
                .thenThrow(new RuntimeException("Policy subsystem unavailable"));

        assertThrows(OrderViolatesPolicyException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });

        verify(paymentGateway, never()).charge(any(), any(), any(), any());
        verify(purchaseRecordRepository, never()).append(any());
    }

    @Test
    void executeCheckout_DiscountEvaluationFails_ThrowsPriceCalcException() {
        mockSuccessfulPolicyValidation();

        when(discountPort.calculateDiscountSummary(any(PurchaseContext.class), any(Money.class)))
                .thenThrow(new RuntimeException("Discount service down"));

        assertThrows(PriceCalcException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });

        verify(paymentGateway, never()).charge(any(), any(), any(), any());
        verify(ticketIssuance, never()).issueTickets(any(), any(), any(), any());
        verify(purchaseRecordRepository, never()).append(any());
    }

    @Test
    void executeCheckout_PaymentGatewayThrows_ThrowsPaymentFailedExceptionAndNotifies() {
        mockSuccessfulPolicyValidation();
        mockNoDiscount();

        when(paymentGateway.charge(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Gateway unavailable"));

        assertThrows(PaymentFailedException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", "DISCOUNT10");
        });

        verify(notificationPort).sendPurchaseFailure(eq(testBuyer), contains("Payment processing error"));
        verify(ticketIssuance, never()).issueTickets(any(), any(), any(), any());
        verify(paymentGateway, never()).refund(any(), any(), any());
        verify(purchaseRecordRepository, never()).append(any());
    }

    // Transactional related tests
    @Test
    void executeCheckout_IsTransactionalAndDoesNotRollbackCompensationOnIssuanceFailure() throws Exception {
        // REQ: PERS-05, INV-10, ROB-01, UC 9, UAT-30
        // V3 requires each use case to be a transaction boundary.
        // For ticket issuance failure after payment, compensation must be committed:
        // refund requested, inventory released, order cancelled, and no purchase record
        // saved.

        Transactional transactional = CheckoutSaga.class
                .getMethod("executeCheckout", String.class, String.class, String.class)
                .getAnnotation(Transactional.class);

        assertEquals(IssuanceFailedException.class, transactional.noRollbackFor()[0]);
    }

    @Test
    void executeCheckout_WhenRefundThrowsAfterIssuanceFailure_StillReleasesInventoryCancelsOrderAndNoReceipt() {
        // REQ: INV-10, ROB-01, UC 9, UAT-30
        mockSuccessfulPolicyValidation();
        mockNoDiscount();

        when(paymentGateway.charge(any(), any(), any(), any()))
                .thenReturn(PaymentResult.successful("TXN-REFUND-FAIL"));

        when(ticketIssuance.issueTickets(any(), any(), any(), any()))
                .thenReturn(IssuanceResult.failed("Ticket system failed"));

        doThrow(new RuntimeException("Refund gateway unavailable"))
                .when(paymentGateway)
                .refund(eq("TXN-REFUND-FAIL"), any(), contains("Ticket issuance failed"));

        Zone mockZone = mockSeatedZone("VIP-ZONE");

        assertThrows(IssuanceFailedException.class, () -> {
            checkoutSaga.executeCheckout(ORDER_ID, "VALID_TOKEN", null);
        });

        verify(paymentGateway, times(1))
                .refund(eq("TXN-REFUND-FAIL"), any(), contains("Ticket issuance failed"));

        verify(mockZone, times(1)).releaseSeat(new SeatId("SEAT-42"));
        verify(zoneRepository, times(1)).save(mockZone);
        verify(orderRepository, times(1)).save(testOrder);
        verify(purchaseRecordRepository, never()).append(any());

        assertEquals(OrderStatus.CANCELLED, testOrder.getStatus());
    }
}