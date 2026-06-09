package com.eventsystem.infrastructure.api.orders;

import com.eventsystem.application.order.ActiveOrderDTO;
import com.eventsystem.application.order.BuyerRefernceDTO;
import com.eventsystem.application.order.OrderService;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderStatus;
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

    @InjectMocks
    private OrdersRestController controller;

    @Test
    void createOrGetActiveOrder_mapsGuestBuyerType_andLotteryOptional() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.buyerType = "GUEST";
        req.sessionId = "sess-1";
        req.memberId = null;
        req.eventId = "EV-1";
        req.lotteryCode = "LOT-1";

        ActiveOrderDTO dto = dto("ORDER-1", BuyerType.GUEST, "sess-1", null, "EV-1");
        when(orderService.createOrGetActiveOrder(any(), eq("EV-1"), eq(Optional.of("LOT-1")))).thenReturn(dto);

        var response = controller.createOrGetActiveOrder(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(dto);

        ArgumentCaptor<BuyerReference> buyerCaptor = ArgumentCaptor.forClass(BuyerReference.class);
        verify(orderService).createOrGetActiveOrder(buyerCaptor.capture(), eq("EV-1"), eq(Optional.of("LOT-1")));
        assertThat(buyerCaptor.getValue().type()).isEqualTo(BuyerType.GUEST);
        assertThat(buyerCaptor.getValue().sessionId()).isEqualTo("sess-1");
    }

    @Test
    void createOrGetActiveOrder_defaultsToMember_whenBuyerTypeNotGuest() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.buyerType = "MEMBER";
        req.sessionId = "sess-2";
        req.memberId = "member-2";
        req.eventId = "EV-2";

        ActiveOrderDTO dto = dto("ORDER-2", BuyerType.MEMBER, "sess-2", "member-2", "EV-2");
        when(orderService.createOrGetActiveOrder(any(), eq("EV-2"), eq(Optional.empty()))).thenReturn(dto);

        controller.createOrGetActiveOrder(req);

        ArgumentCaptor<BuyerReference> buyerCaptor = ArgumentCaptor.forClass(BuyerReference.class);
        verify(orderService).createOrGetActiveOrder(buyerCaptor.capture(), eq("EV-2"), eq(Optional.empty()));
        assertThat(buyerCaptor.getValue().type()).isEqualTo(BuyerType.MEMBER);
        assertThat(buyerCaptor.getValue().memberId()).isEqualTo("member-2");
    }

    @Test
    void createMethods_validateEventId() {
        CreateOrderRequest req = new CreateOrderRequest();

        assertThatThrownBy(() -> controller.createOrGetActiveOrder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId is required");

        assertThatThrownBy(() -> controller.createOrGetActiveOrder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId is required");

        assertThatThrownBy(() -> controller.createNewOrderStrict(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId is required");
    }

    @Test
    void reserve_and_release_validateRequestBody() {
        AddItemRequest reserve = new AddItemRequest();
        reserve.zoneId = "zone-1";

        assertThatThrownBy(() -> controller.addItemToOrder("ORD-1", reserve))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zoneId and seatId are required");

        RemoveItemRequest release = new RemoveItemRequest();
        release.seatId = "seat-1";

        assertThatThrownBy(() -> controller.removeItemFromOrder("ORD-1", release))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zoneId and seatId are required");
    }

    @Test
    void reserve_and_release_delegate_and_returnAccepted() {
        AddItemRequest reserve = new AddItemRequest();
        reserve.zoneId = "zone-1";
        reserve.seatId = "seat-1";

        RemoveItemRequest release = new RemoveItemRequest();
        release.zoneId = "zone-1";
        release.seatId = "seat-1";

        var reserveResponse = controller.addItemToOrder("ORD-1", reserve);
        var releaseResponse = controller.removeItemFromOrder("ORD-1", release);

        verify(orderService).addItemToOrder("ORD-1", "zone-1", "seat-1", 1);
        verify(orderService).removeItemFromOrder("ORD-1", "zone-1", "seat-1", 1);
        assertThat(reserveResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(releaseResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void getOrder_validatesInput_and_delegates() {
        assertThatThrownBy(() -> controller.getOrder(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId is required");

        ActiveOrderDTO dto = dto("ORD-9", BuyerType.MEMBER, "sess-9", "member-9", "EV-9");
        when(orderService.getOrderById("ORD-9")).thenReturn(dto);

        var response = controller.getOrder("ORD-9");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(dto);
    }

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
