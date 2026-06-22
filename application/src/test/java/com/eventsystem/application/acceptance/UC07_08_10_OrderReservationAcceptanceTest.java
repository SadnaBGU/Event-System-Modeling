package com.eventsystem.application.acceptance;

import com.eventsystem.application.appexceptions.AlreadyExistsOrderException;
import com.eventsystem.application.appexceptions.ZoneApplicationException;
import com.eventsystem.application.order.ActiveOrderDTO;
import com.eventsystem.application.order.OrderItemDTO;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderStatus;
import com.eventsystem.domain.zone.SeatStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UC07_08_10_OrderReservationAcceptanceTest {

    // REQ: USR-07, INV-08
    // UC: UC 7 - Ticket Selection and Reservation
    // UAT: UAT-16 - Successful Reservation
    @Test
    void reserveAvailableSeat_createsActiveOrderLocksSeatAndStoresOrderItem() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.createSeatedZone("event-1", "zone-vip", "A-1", "100.00");

        ActiveOrderDTO order = app.createStrictOrder(buyer, "event-1");
        app.reserveSeat(order.orderId(), "zone-vip", "A-1");

        assertThat(app.order(order.orderId()).getItems()).hasSize(1);
        assertThat(app.order(order.orderId()).getItems().get(0).getSeatId()).isEqualTo("A-1");
        assertThat(app.zone("zone-vip").rows().get(0).seats().get(0).status()).isEqualTo(SeatStatus.RESERVED);
        assertThat(app.zone("zone-vip").getAvailableCount()).isZero();
    }

    // REQ: INV-07
    // UC: UC 7 - Ticket Selection and Reservation
    // UAT: UAT-19 - Existing Order Check
    @Test
    void reserveForSameBuyerAndEvent_whenActiveOrderExists_isRejectedByStrictReservation() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference buyer = app.memberBuyer("buyer-1");

        app.createStrictOrder(buyer, "event-1");

        assertThatThrownBy(() -> app.createStrictOrder(buyer, "event-1"))
                .isInstanceOf(AlreadyExistsOrderException.class)
                .hasMessageContaining("active order");
    }

    // REQ: NFR-02, INV-09
    // UC: UC 7 - Ticket Selection and Reservation
    // UAT: UAT-22 - Tickets Unavailable
    @Test
    void reserveSeat_whenAlreadyReservedByAnotherOrder_isRejectedAndSecondOrderRemainsEmpty() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference firstBuyer = app.memberBuyer("buyer-1");
        BuyerReference secondBuyer = app.memberBuyer("buyer-2");
        app.createSeatedZone("event-1", "zone-vip", "A-1", "100.00");

        String firstOrderId = app.createStrictOrder(firstBuyer, "event-1").orderId();
        String secondOrderId = app.createStrictOrder(secondBuyer, "event-1").orderId();
        app.reserveSeat(firstOrderId, "zone-vip", "A-1");

        assertThatThrownBy(() -> app.reserveSeat(secondOrderId, "zone-vip", "A-1"))
                .isInstanceOf(ZoneApplicationException.class);

        assertThat(app.order(firstOrderId).getItems()).hasSize(1);
        assertThat(app.order(secondOrderId).getItems()).isEmpty();
        assertThat(app.zone("zone-vip").rows().get(0).seats().get(0).status()).isEqualTo(SeatStatus.RESERVED);
    }

    // REQ: USR-09, INV-08
    // UC: UC 8 - Manage Active Order
    // UAT: UAT-23 - Manage Order - Remove Ticket
    @Test
    void removeTicketFromActiveOrder_releasesSeatBackToInventory() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.createSeatedZone("event-1", "zone-vip", "A-1", "100.00");
        String orderId = app.createStrictOrder(buyer, "event-1").orderId();
        app.reserveSeat(orderId, "zone-vip", "A-1");

        app.orderService.removeItemFromOrder(orderId, "zone-vip", "A-1", 1);

        assertThat(app.order(orderId).getItems()).isEmpty();
        assertThat(app.zone("zone-vip").rows().get(0).seats().get(0).status()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(app.zone("zone-vip").getAvailableCount()).isEqualTo(1);
    }

    // REQ: USR-08, INV-08
    // UC: UC 8 - Manage Active Order
    // UAT: UAT-24 - Active Order Timeout
    @Test
    void sweepExpiredOrders_releasesReservedInventoryAndMarksOrderExpired() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.createSeatedZone("event-1", "zone-vip", "A-1", "100.00");
        app.zone("zone-vip").reserveSeat(app.seatId("A-1"));
        app.zones.save(app.zone("zone-vip"));
        var expiredOrder = app.createExpiredOrderWithReservedSeat(buyer, "event-1", "zone-vip", "A-1", "100.00");

        app.orderService.sweepExpiredOrders();

        assertThat(app.order(expiredOrder.getOrderId()).getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(app.zone("zone-vip").rows().get(0).seats().get(0).status()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(app.zone("zone-vip").getAvailableCount()).isEqualTo(1);
    }

    // REQ: USR-09, INV-08
    // UC: UC 8 - Manage Active Order
    // UAT: UAT-25 - Manage Expired Order
    @Test
    void modifyExpiredOrder_isRejectedAndDoesNotChangeInventory() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.createSeatedZone("event-1", "zone-vip", "A-1", "100.00");
        app.zone("zone-vip").reserveSeat(app.seatId("A-1"));
        app.zones.save(app.zone("zone-vip"));
        var expiredOrder = app.createExpiredOrderWithReservedSeat(buyer, "event-1", "zone-vip", "A-1", "100.00");

        assertThatThrownBy(() -> app.orderService.removeItemFromOrder(expiredOrder.getOrderId(), "zone-vip", "A-1", 1))
                .isInstanceOf(com.eventsystem.domain.domainexceptions.ActiveOrderHasExpiredException.class);

        assertThat(app.zone("zone-vip").rows().get(0).seats().get(0).status()).isEqualTo(SeatStatus.RESERVED);
        assertThat(app.order(expiredOrder.getOrderId()).getStatus()).isEqualTo(OrderStatus.ACTIVE);
    }

    // REQ: USR-12, INV-07
    // UC: UC 10 - Member Logout with Active Reservation
    // UAT: UAT-31 - Resume Active Order
    @Test
    void memberLogsBackInBeforeTimeout_existingActiveOrderCanBeFoundAndResumed() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.createSeatedZone("event-1", "zone-vip", "A-1", "100.00");
        String orderId = app.createOrder(buyer, "event-1").orderId();
        app.reserveSeat(orderId, "zone-vip", "A-1");

        ActiveOrderDTO resumed = app.createOrder(buyer, "event-1");

        assertThat(resumed.orderId()).isEqualTo(orderId);
        assertThat(resumed.items()).extracting(OrderItemDTO::seatId).containsExactly("A-1");
        assertThat(app.zone("zone-vip").rows().get(0).seats().get(0).status()).isEqualTo(SeatStatus.RESERVED);
    }

    // REQ: USR-12, INV-08
    // UC: UC 10 - Member Logout with Active Reservation
    // UAT: UAT-32 - Active Order Timeout
    @Test
    void memberLogsBackInAfterTimeout_sweepExpiresPreviousOrderAndNextOrderIsNew() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.createSeatedZone("event-1", "zone-vip", "A-1", "100.00");
        app.zone("zone-vip").reserveSeat(app.seatId("A-1"));
        app.zones.save(app.zone("zone-vip"));
        var expiredOrder = app.createExpiredOrderWithReservedSeat(buyer, "event-1", "zone-vip", "A-1", "100.00");

        app.orderService.sweepExpiredOrders();
        ActiveOrderDTO newOrder = app.createOrder(buyer, "event-1");

        assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(newOrder.orderId()).isNotEqualTo(expiredOrder.getOrderId());
        assertThat(app.zone("zone-vip").rows().get(0).seats().get(0).status()).isEqualTo(SeatStatus.AVAILABLE);
    }
}
