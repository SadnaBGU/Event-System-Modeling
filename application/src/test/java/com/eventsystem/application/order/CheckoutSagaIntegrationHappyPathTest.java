package com.eventsystem.application.order;

import com.eventsystem.application.event.IEventQueryPort;
import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.application.policy.IDiscountApplicationPort;
import com.eventsystem.application.policy.IPurchasePolicyValidationPort;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.ZoneType;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.IActiveOrderRepository;
import com.eventsystem.domain.order.OrderFactory;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.order.OrderStatus;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.purchaserecord.IPurchaseRecordRepository;
import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CheckoutSagaIntegrationHappyPathTest {

    private PurchaseContext context = new PurchaseContext(
            new EventId("event-1"),
            new CompanyId("company-1"),
            List.of(new ZoneId("zone-1")),
            LocalDate.now().minusYears(25),
            null
    );

    @Test
    void happyPath_appendsPurchaseAndSendsNotification() {
        IActiveOrderRepository orderRepo = mock(IActiveOrderRepository.class);
        IPurchaseRecordRepository purchaseRepo = mock(IPurchaseRecordRepository.class);
        IPaymentGatewayPort paymentGateway = mock(IPaymentGatewayPort.class);
        ITicketIssuancePort ticketIssuance = mock(ITicketIssuancePort.class);
        INotificationPort notificationPort = mock(INotificationPort.class);
        IZoneRepository zoneRepo = mock(IZoneRepository.class);
        IEventQueryPort eventQuery = mock(IEventQueryPort.class);
        IPurchasePolicyValidationPort purchasePolicyPort  = mock(IPurchasePolicyValidationPort.class);
        IDiscountApplicationPort discountPort  = mock(IDiscountApplicationPort.class);

        

        CheckoutSaga saga = new CheckoutSaga(orderRepo, purchaseRepo, paymentGateway, ticketIssuance, notificationPort, zoneRepo,  purchasePolicyPort, discountPort, eventQuery);

        // create an active order with a single item
        OrderFactory factory = new OrderFactory();
        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, "sess-1", "member-1");
        ActiveOrder order = factory.createOrder(buyer, "event-1", Instant.now().plusSeconds(60));
        order.addItem(new OrderItem("zone-1", "seat-1", 1, Money.of(BigDecimal.valueOf(50), "USD")));
        when(orderRepo.findById(order.getOrderId())).thenReturn(Optional.of(order));
        when(purchasePolicyPort.createPurchaseContext(new EventId(order.getEventId()), order.getBuyerRef(), order.getItems())).thenReturn(context);
        PolicyValidationResult successResult = new PolicyValidationResult(true, null);
        when(purchasePolicyPort.evaluatePurchasePolicyFor(any())).thenReturn(successResult);
        DiscountSnapshot discount = new DiscountSnapshot("NONE", Money.of(BigDecimal.ZERO, "USD"));
        when(discountPort.generateDiscountSnapshot(any(), any())).thenReturn(discount);
        when(eventQuery.getEventSnapshot(anyString())).thenReturn(new EventSnapshot("event-1", "Concert", "ProdCo", LocalDate.now(), "Venue"));

        when(paymentGateway.charge(eq(order.getOrderId()), any(), eq(order.getBuyerRef()), anyString()))
                .thenReturn(PaymentResult.successful("TXN-1"));

        when(ticketIssuance.issueTickets(eq(order.getEventId()), eq(order.getOrderId()), anyList(), eq(order.getBuyerRef())))
                .thenReturn(IssuanceResult.successful("ISS-1"));

        // execute
        doAnswer(invocation -> {
                Runnable action = invocation.getArgument(1);
                action.run();
                return null;
        }).when(zoneRepo).withLock(any(ZoneId.class), any(Runnable.class));

        Zone mockZone = mock(Zone.class);
        when(mockZone.zoneType()).thenReturn(ZoneType.SEATED);
        when(zoneRepo.findById(new ZoneId("zone-1"))).thenReturn(Optional.of(mockZone));
        CheckoutResult result = saga.executeCheckout(order.getOrderId(), "token-123", null);

        //verify checkoutresult:
        assertEquals(List.of("ISS-1"), result.issuedTicketCodes());
        assertEquals(OrderStatus.CHECKED_OUT.name(), result.orderStatus());

        verify(mockZone).markSold(new SeatId("seat-1"));
        verify(zoneRepo).save(mockZone);
        // verify purchase record appended and notification sent
        ArgumentCaptor<PurchaseRecord> captor = ArgumentCaptor.forClass(PurchaseRecord.class);
        verify(purchaseRepo).append(captor.capture());
        PurchaseRecord rec = captor.getValue();
        assertNotNull(rec.getRecordId());

        verify(orderRepo).save(order);
        verify(notificationPort).sendPurchaseSuccess(order.getBuyerRef(), rec.getRecordId());
        assertEquals(OrderStatus.CHECKED_OUT, order.getStatus());
    }
}
