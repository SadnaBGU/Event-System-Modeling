package com.eventsystem.application.order;

import com.eventsystem.application.event.IEventQueryPort;
import com.eventsystem.application.event.IZoneRepository;
import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderFactory;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.order.OrderStatus;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CheckoutSagaIntegrationHappyPathTest {

    @Test
    void happyPath_appendsPurchaseAndSendsNotification() {
        IActiveOrderRepository orderRepo = mock(IActiveOrderRepository.class);
        IPurchaseRecordRepository purchaseRepo = mock(IPurchaseRecordRepository.class);
        IPaymentGatewayPort paymentGateway = mock(IPaymentGatewayPort.class);
        ITicketIssuancePort ticketIssuance = mock(ITicketIssuancePort.class);
        INotificationPort notificationPort = mock(INotificationPort.class);
        IZoneRepository zoneRepo = mock(IZoneRepository.class);
        IEventQueryPort eventQuery = mock(IEventQueryPort.class);

        CheckoutSaga saga = new CheckoutSaga(orderRepo, purchaseRepo, paymentGateway, ticketIssuance, notificationPort, zoneRepo, eventQuery);

        // create an active order with a single item
        OrderFactory factory = new OrderFactory();
        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, "sess-1", "member-1");
        ActiveOrder order = factory.createOrder(buyer, "event-1", Instant.now().plusSeconds(60));
        order.addItem(new OrderItem("zone-1", "seat-1", 1, Money.of(BigDecimal.valueOf(50), "USD")));

        when(orderRepo.findById(order.getOrderId())).thenReturn(Optional.of(order));
        when(eventQuery.validatePurchasePolicy(anyString(), any(), anyList())).thenReturn(true);
        DiscountSnapshot discount = new DiscountSnapshot("NONE", Money.of(BigDecimal.ZERO, "USD"));
        when(eventQuery.applyDiscount(anyString(), any(), any())).thenReturn(discount);
        when(eventQuery.getEventSnapshot(anyString())).thenReturn(new EventSnapshot("event-1", "Concert", "ProdCo", LocalDate.now(), "Venue"));

        when(paymentGateway.charge(eq(order.getOrderId()), any(), eq(order.getBuyerRef()), anyString()))
                .thenReturn(PaymentResult.successful("TXN-1"));

        when(ticketIssuance.issueTickets(eq(order.getEventId()), eq(order.getOrderId()), anyList(), eq(order.getBuyerRef())))
                .thenReturn(IssuanceResult.successful("ISS-1"));

        // execute
        saga.executeCheckout(order.getOrderId(), "token-123", null);

        // verify purchase record appended and notification sent
        ArgumentCaptor<PurchaseRecord> captor = ArgumentCaptor.forClass(PurchaseRecord.class);
        verify(purchaseRepo).append(captor.capture());
        PurchaseRecord rec = captor.getValue();
        assertNotNull(rec.recordId());

        verify(orderRepo).save(order);
        verify(notificationPort).sendPurchaseSuccess(order.getBuyerRef(), rec.recordId());
        assertEquals(OrderStatus.CHECKED_OUT, order.getStatus());
    }
}
