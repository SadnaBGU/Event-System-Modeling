package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresActiveOrderRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresActiveOrderRepository.class)
class PostgresActiveOrderRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresActiveOrderRepository activeOrderRepository;

    @Autowired
    private EntityManager em;

    @Test
    void saveAndFindById_persistsActiveOrder() {
        ActiveOrder order = order("order-1");

        activeOrderRepository.save(order);
        em.flush();
        em.clear();

        ActiveOrder found = activeOrderRepository.findById("order-1").orElseThrow();

        assertThat(found.getOrderId()).isEqualTo("order-1");
        assertThat(found.getBuyerRef()).isEqualTo(new BuyerReference(BuyerType.MEMBER, null, "member-1"));
        assertThat(found.getEventId()).isEqualTo("event-1");
        assertThat(found.getItems()).isEmpty();
    }

    @Test
    void findById_returnsEmptyForMissingOrder() {
        assertThat(activeOrderRepository.findById("missing-order")).isEmpty();
    }

    @Test
    void addItem_persistsElementCollectionItem() {
        ActiveOrder order = order("order-2");
        order.addItem(item("zone-a", "A-1", 1, "25.00"));

        activeOrderRepository.save(order);
        em.flush();
        em.clear();

        ActiveOrder found = activeOrderRepository.findById("order-2").orElseThrow();

        assertThat(found.getItems()).hasSize(1);
        assertThat(found.getItems().get(0).getZoneId()).isEqualTo("zone-a");
        assertThat(found.getItems().get(0).getSeatId()).isEqualTo("A-1");
        assertThat(found.getItems().get(0).getUnitPrice()).isEqualTo(Money.of(new BigDecimal("25.00"), "ILS"));
    }

    @Test
    void orphanRemoval_removesDeletedElementCollectionRows() {
        ActiveOrder order = order("order-3");
        order.addItem(item("zone-a", "A-1", 1, "25.00"));
        order.addItem(item("zone-b", "B-1", 1, "30.00"));
        activeOrderRepository.save(order);
        em.flush();
        em.clear();

        ActiveOrder found = activeOrderRepository.findById("order-3").orElseThrow();
        found.removeItem("zone-a", "A-1");
        activeOrderRepository.save(found);
        em.flush();
        em.clear();

        ActiveOrder updated = activeOrderRepository.findById("order-3").orElseThrow();
        Number rowCount = (Number) em.createNativeQuery("select count(*) from active_order_items where order_id = 'order-3'")
                .getSingleResult();

        assertThat(updated.getItems()).hasSize(1);
        assertThat(updated.getItems().get(0).getZoneId()).isEqualTo("zone-b");
        assertThat(rowCount.longValue()).isEqualTo(1L);
    }

    @Test
    void findByBuyerAndEvent_returnsOnlyMatchingOrder() {
        BuyerReference targetBuyer = new BuyerReference(BuyerType.MEMBER, null, "member-target");
        ActiveOrder target = order("order-target", targetBuyer, "event-target", Instant.now().plus(30, ChronoUnit.MINUTES));
        ActiveOrder sameBuyerOtherEvent = order("order-other-event", targetBuyer, "event-other", Instant.now().plus(30, ChronoUnit.MINUTES));
        ActiveOrder sameEventOtherBuyer = order("order-other-buyer", new BuyerReference(BuyerType.MEMBER, null, "member-other"), "event-target", Instant.now().plus(30, ChronoUnit.MINUTES));
        activeOrderRepository.save(target);
        activeOrderRepository.save(sameBuyerOtherEvent);
        activeOrderRepository.save(sameEventOtherBuyer);
        em.flush();
        em.clear();

        ActiveOrder found = activeOrderRepository.findByBuyerAndEvent(targetBuyer, "event-target").orElseThrow();

        assertThat(found.getOrderId()).isEqualTo("order-target");
    }

    @SuppressWarnings("deprecation")
    @Test
    void findExpired_returnsOnlyOrdersPastReservationExpiry() {
        ActiveOrder expired = order("order-expired", new BuyerReference(BuyerType.MEMBER, null, "member-expired"), "event-expired", Instant.now().minus(5, ChronoUnit.MINUTES));
        ActiveOrder notExpired = order("order-current", new BuyerReference(BuyerType.MEMBER, null, "member-current"), "event-current", Instant.now().plus(30, ChronoUnit.MINUTES));
        activeOrderRepository.save(expired);
        activeOrderRepository.save(notExpired);
        em.flush();
        em.clear();

        assertThat(activeOrderRepository.findExpired()).isPresent()
                .get()
                .extracting(orders -> orders.stream().map(ActiveOrder::getOrderId).toList())
                .asList()
                .contains("order-expired")
                .doesNotContain("order-current");
    }

    @Test
    void delete_removesOrderAndOwnedItems() {
        ActiveOrder order = order("order-delete");
        order.addItem(item("zone-a", "A-1", 1, "25.00"));
        activeOrderRepository.save(order);
        em.flush();
        em.clear();

        activeOrderRepository.delete("order-delete");
        em.flush();
        em.clear();

        Number itemRows = (Number) em.createNativeQuery("select count(*) from active_order_items where order_id = 'order-delete'")
                .getSingleResult();

        assertThat(activeOrderRepository.findById("order-delete")).isEmpty();
        assertThat(itemRows.longValue()).isZero();
    }

    private static ActiveOrder order(String orderId) {
        return order(
                orderId,
                new BuyerReference(BuyerType.MEMBER, null, "member-1"),
                "event-1",
                Instant.now().plus(10, ChronoUnit.DAYS)
        );
    }

    private static ActiveOrder order(String orderId, BuyerReference buyer, String eventId, Instant reservationExpiry) {
        return new ActiveOrder(
                orderId,
                buyer,
                eventId,
                reservationExpiry
        );
    }

    private static OrderItem item(String zoneId, String seatId, int quantity, String amount) {
        return new OrderItem(zoneId, seatId, quantity, Money.of(new BigDecimal(amount), "ILS"));
    }
}
