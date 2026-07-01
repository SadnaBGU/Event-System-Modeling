package com.eventsystem.infrastructure.api.orders;

import com.eventsystem.application.order.ActiveOrderDTO;
import com.eventsystem.application.order.BuyerRefernceDTO;
import com.eventsystem.application.order.OrderPricingPreviewDTO;
import com.eventsystem.application.order.OrderService;
import com.eventsystem.application.order.QueueService;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderStatus;
import com.eventsystem.infrastructure.api.order.ApplyDiscountRequest;
import com.eventsystem.infrastructure.api.order.CreateOrderRequest;
import com.eventsystem.infrastructure.api.order.OrdersRestController;
import com.eventsystem.infrastructure.api.order.RemoveItemRequest;
import com.eventsystem.infrastructure.api.order.AddItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdersRestControllerUnitTest {

    @Mock
    private OrderService orderService;

    @Mock
    private QueueService queueService;

    @InjectMocks
    private OrdersRestController controller;

    // =========================================================
    // createOrGetActiveOrder Branches
    // =========================================================

    @Test
    void createOrGetActiveOrder_validGuest_returnsOk() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.buyerType = "GUEST";
        req.sessionId = "sess-1";
        req.eventId = "EV-1";
        req.lotteryCode = "LOT-1";

        ActiveOrderDTO dto = dto("ORDER-1", BuyerType.GUEST, "sess-1", null, "EV-1");
        when(orderService.createOrGetActiveOrder(any(), eq("EV-1"), eq(Optional.of("LOT-1")))).thenReturn(dto);

        var response = controller.createOrGetActiveOrder(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<BuyerReference> buyerCaptor = ArgumentCaptor.forClass(BuyerReference.class);
        verify(orderService).createOrGetActiveOrder(buyerCaptor.capture(), eq("EV-1"), eq(Optional.of("LOT-1")));
        assertThat(buyerCaptor.getValue().type()).isEqualTo(BuyerType.GUEST);
    }

    @Test
    void createOrGetActiveOrder_validMember_returnsOk() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.buyerType = "MEMBER";
        req.eventId = "EV-2";

        ActiveOrderDTO dto = dto("ORDER-2", BuyerType.MEMBER, null, "mem-2", "EV-2");
        when(orderService.createOrGetActiveOrder(any(), eq("EV-2"), eq(Optional.empty()))).thenReturn(dto);

        controller.createOrGetActiveOrder(req);

        ArgumentCaptor<BuyerReference> buyerCaptor = ArgumentCaptor.forClass(BuyerReference.class);
        verify(orderService).createOrGetActiveOrder(buyerCaptor.capture(), eq("EV-2"), eq(Optional.empty()));
        assertThat(buyerCaptor.getValue().type()).isEqualTo(BuyerType.MEMBER);
    }

    @Test
    void createOrGetActiveOrder_invalidInputs_throwException() {
        CreateOrderRequest nullIdReq = new CreateOrderRequest();
        nullIdReq.eventId = null;
        
        CreateOrderRequest blankIdReq = new CreateOrderRequest();
        blankIdReq.eventId = "   ";

        assertThatThrownBy(() -> controller.createOrGetActiveOrder(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("eventId is required");
        assertThatThrownBy(() -> controller.createOrGetActiveOrder(nullIdReq))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("eventId is required");
        assertThatThrownBy(() -> controller.createOrGetActiveOrder(blankIdReq))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("eventId is required");
    }

    // =========================================================
    // createNewOrderStrict Branches
    // =========================================================

    @Test
    void createNewOrderStrict_validGuest_returnsOk() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.buyerType = "guest"; // Testing case insensitivity
        req.eventId = "EV-1";

        ActiveOrderDTO dto = dto("ORDER-1", BuyerType.GUEST, "sess-1", null, "EV-1");
        when(orderService.createNewOrderStrict(any(), eq("EV-1"))).thenReturn(dto);

        var response = controller.createNewOrderStrict(req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        ArgumentCaptor<BuyerReference> buyerCaptor = ArgumentCaptor.forClass(BuyerReference.class);
        verify(orderService).createNewOrderStrict(buyerCaptor.capture(), eq("EV-1"));
        assertThat(buyerCaptor.getValue().type()).isEqualTo(BuyerType.GUEST);
    }

    @Test
    void createNewOrderStrict_validMember_returnsOk() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.buyerType = "OTHER"; 
        req.eventId = "EV-1";

        ActiveOrderDTO dto = dto("ORDER-1", BuyerType.MEMBER, "sess-1", null, "EV-1");
        when(orderService.createNewOrderStrict(any(), eq("EV-1"))).thenReturn(dto);

        controller.createNewOrderStrict(req);
        
        ArgumentCaptor<BuyerReference> buyerCaptor = ArgumentCaptor.forClass(BuyerReference.class);
        verify(orderService).createNewOrderStrict(buyerCaptor.capture(), eq("EV-1"));
        assertThat(buyerCaptor.getValue().type()).isEqualTo(BuyerType.MEMBER);
    }

    @Test
    void createNewOrderStrict_invalidInputs_throwException() {
        CreateOrderRequest nullIdReq = new CreateOrderRequest();
        CreateOrderRequest blankIdReq = new CreateOrderRequest();
        blankIdReq.eventId = "";

        assertThatThrownBy(() -> controller.createNewOrderStrict(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.createNewOrderStrict(nullIdReq))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.createNewOrderStrict(blankIdReq))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================
    // addItemToOrder Branches
    // =========================================================

    @Test
    void addItemToOrder_validQuantities_delegatesProperly() {
        // Valid custom quantity
        AddItemRequest reqWithQty = new AddItemRequest();
        reqWithQty.zoneId = "ZONE-1";
        reqWithQty.seatId = "SEAT-1";
        reqWithQty.quantity = 5;

        // Null quantity -> defaults to 1
        AddItemRequest reqNullQty = new AddItemRequest();
        reqNullQty.zoneId = "ZONE-1";

        // Negative/Zero quantity -> defaults to 1
        AddItemRequest reqNegativeQty = new AddItemRequest();
        reqNegativeQty.zoneId = "ZONE-1";
        reqNegativeQty.quantity = -3;

        controller.addItemToOrder("ORD-1", reqWithQty);
        controller.addItemToOrder("ORD-1", reqNullQty);
        controller.addItemToOrder("ORD-1", reqNegativeQty);

        verify(orderService).addItemToOrder("ORD-1", "ZONE-1", "SEAT-1", 5);
        verify(orderService, org.mockito.Mockito.times(2)).addItemToOrder("ORD-1", "ZONE-1", null, 1);
    }

    @Test
    void addItemToOrder_invalidInputs_throwException() {
        AddItemRequest nullZone = new AddItemRequest();
        AddItemRequest blankZone = new AddItemRequest();
        blankZone.zoneId = "  ";

        assertThatThrownBy(() -> controller.addItemToOrder("ORD-1", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.addItemToOrder("ORD-1", nullZone))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.addItemToOrder("ORD-1", blankZone))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================
    // removeItemFromOrder Branches
    // =========================================================

    @Test
    void removeItemFromOrder_validQuantities_delegatesProperly() {
        RemoveItemRequest reqWithQty = new RemoveItemRequest();
        reqWithQty.zoneId = "ZONE-1";
        reqWithQty.seatId = "SEAT-1";
        reqWithQty.quantity = 5;

        RemoveItemRequest reqNullQty = new RemoveItemRequest();
        reqNullQty.zoneId = "ZONE-1";

        RemoveItemRequest reqZeroQty = new RemoveItemRequest();
        reqZeroQty.zoneId = "ZONE-1";
        reqZeroQty.quantity = 0;

        controller.removeItemFromOrder("ORD-1", reqWithQty);
        controller.removeItemFromOrder("ORD-1", reqNullQty);
        controller.removeItemFromOrder("ORD-1", reqZeroQty);

        verify(orderService).removeItemFromOrder("ORD-1", "ZONE-1", "SEAT-1", 5);
        verify(orderService, org.mockito.Mockito.times(2)).removeItemFromOrder("ORD-1", "ZONE-1", null, 1);
    }

    @Test
    void removeItemFromOrder_invalidInputs_throwException() {
        RemoveItemRequest nullZone = new RemoveItemRequest();
        RemoveItemRequest blankZone = new RemoveItemRequest();
        blankZone.zoneId = "";

        assertThatThrownBy(() -> controller.removeItemFromOrder("ORD-1", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.removeItemFromOrder("ORD-1", nullZone))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.removeItemFromOrder("ORD-1", blankZone))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================
    // getOrder Branches
    // =========================================================

    @Test
    void getOrder_invalidInputs_throwException() {
        assertThatThrownBy(() -> controller.getOrder(null))
                .isInstanceOf(IllegalArgumentException.class);
        
        assertThatThrownBy(() -> controller.getOrder("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getOrder_validInput_returnsOk() {
        ActiveOrderDTO dto = dto("ORD-9", BuyerType.MEMBER, "sess-9", "member-9", "EV-9");
        when(orderService.getOrderById("ORD-9")).thenReturn(dto);

        var response = controller.getOrder("ORD-9");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(dto);
    }

    // =========================================================
    // previewDiscount Branches
    // =========================================================

    @Test
    void previewDiscount_validInput_returnsOk() {
        ApplyDiscountRequest req = new ApplyDiscountRequest();
        req.discountCode = "SAVE10";
        OrderPricingPreviewDTO dto = new OrderPricingPreviewDTO(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(90),
                "USD"
        );
        when(orderService.previewDiscount("ORD-1", "SAVE10")).thenReturn(dto);

        var response = controller.previewDiscount("ORD-1", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(dto);
    }

    @Test
    void previewDiscount_invalidInputs_throwException() {
        ApplyDiscountRequest nullCodeReq = new ApplyDiscountRequest();
        ApplyDiscountRequest blankCodeReq = new ApplyDiscountRequest();
        blankCodeReq.discountCode = "   ";

        assertThatThrownBy(() -> controller.previewDiscount(null, blankCodeReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId is required");
        assertThatThrownBy(() -> controller.previewDiscount("ORD-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discountCode is required");
        assertThatThrownBy(() -> controller.previewDiscount("ORD-1", nullCodeReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discountCode is required");
        assertThatThrownBy(() -> controller.previewDiscount("ORD-1", blankCodeReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discountCode is required");
    }

    // =========================================================
    // Helpers
    // =========================================================

    private static ActiveOrderDTO dto(String orderId, BuyerType type, String sessionId, String memberId, String eventId) {
        return new ActiveOrderDTO(
                orderId,
                new BuyerRefernceDTO(type, sessionId, memberId),
                eventId,
                List.of(),
                Instant.parse("2026-01-01T00:00:00Z"),
                OrderStatus.ACTIVE,
                1L
        );
    }
}