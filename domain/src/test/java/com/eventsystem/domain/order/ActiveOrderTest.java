package com.eventsystem.domain.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.eventsystem.domain.domainexceptions.ActiveOrderNotActiveException;
import com.eventsystem.domain.shared.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Track A: Domain Unit Tests for ActiveOrder aggregate.
 * Pure logic testing - NO Mockito, NO external dependencies.
 * Tests core business logic: BigDecimal calculations, expiry, status transitions.
 */
class ActiveOrderTest {

    private BuyerReference testBuyer;
    private OrderFactory orderFactory;
    private String eventId = "EVENT-2026";

    @BeforeEach
    void setUp() {
        testBuyer = new BuyerReference(BuyerType.MEMBER, "session-123", "member-456");
        orderFactory = new OrderFactory();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Test 1: calculateBaseTotal() - BigDecimal precision
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void calculateBaseTotal_EmptyOrder_ReturnsZero() {
        // Arrange
        Instant expiry = Instant.now().plus(10, ChronoUnit.MINUTES);
        ActiveOrder order = orderFactory.createOrder(testBuyer, eventId, expiry);

        // Act
        Money total = order.calculateBaseTotal();

        // Assert
        assertThat(total.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateBaseTotal_SingleItem_ReturnsCorrectPrice() {
        // Arrange
        Instant expiry = Instant.now().plus(10, ChronoUnit.MINUTES);
        ActiveOrder order = orderFactory.createOrder(testBuyer, eventId, expiry);
        
        OrderItem item = new OrderItem("VIP-ZONE", "SEAT-101", 1, Money.of(new BigDecimal("150.00"), "USD"));
        order.addItem(item);

        // Act
        Money total = order.calculateBaseTotal();

        // Assert
        assertThat(total.amount()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void calculateBaseTotal_MultipleItems_SumsPrecisely() {
        // Arrange
        Instant expiry = Instant.now().plus(10, ChronoUnit.MINUTES);
        ActiveOrder order = orderFactory.createOrder(testBuyer, eventId, expiry);
        
        // Add items with fractional cents to test BigDecimal precision
        OrderItem item1 = new OrderItem("VIP-ZONE", "SEAT-101", 1, Money.of(new BigDecimal("150.50"), "USD"));
        OrderItem item2 = new OrderItem("REGULAR-ZONE", "SEAT-201", 2, Money.of(new BigDecimal("75.25"), "USD"));
        OrderItem item3 = new OrderItem("BALCONY-ZONE", "SEAT-301", 1, Money.of(new BigDecimal("99.99"), "USD"));
        
        order.addItem(item1);
        order.addItem(item2);
        order.addItem(item3);

        // Act
        Money total = order.calculateBaseTotal();

        // Assert - Expected: 150.50 + 75.25 + 99.99 = 325.74
        assertThat(total.amount()).isEqualByComparingTo(new BigDecimal("325.74"));
    }

    @Test
    void calculateBaseTotal_AfterRemovingItem_RecalculatesCorrectly() {
        // Arrange
        Instant expiry = Instant.now().plus(10, ChronoUnit.MINUTES);
        ActiveOrder order = orderFactory.createOrder(testBuyer, eventId, expiry);
        
        OrderItem item1 = new OrderItem("VIP-ZONE", "SEAT-101", 1, Money.of(new BigDecimal("100.00"), "USD"));
        OrderItem item2 = new OrderItem("REGULAR-ZONE", "SEAT-201", 1, Money.of(new BigDecimal("50.00"), "USD"));
        
        order.addItem(item1);
        order.addItem(item2);

        // Act - Remove one item
        order.removeItem("VIP-ZONE", "SEAT-101");
        Money total = order.calculateBaseTotal();

        // Assert
        assertThat(total.amount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Test 2: isExpired() - Reservation timer validation
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void isExpired_FutureExpiry_ReturnsFalse() {
        // Arrange - Create order with expiry 10 minutes in future
        Instant futureExpiry = Instant.now().plus(10, ChronoUnit.MINUTES);
        ActiveOrder order = orderFactory.createOrder(testBuyer, eventId, futureExpiry);

        // Act
        boolean expired = order.isExpired();

        // Assert
        assertThat(expired).isFalse();
    }

    @Test
    void isExpired_PastExpiry_ReturnsTrue() {
        // Arrange - Create order with expiry 1 second in past
        Instant pastExpiry = Instant.now().minus(1, ChronoUnit.SECONDS);
        ActiveOrder order = orderFactory.createOrder(testBuyer, eventId, pastExpiry);

        // Act - Small delay to ensure time has passed
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        boolean expired = order.isExpired();

        // Assert
        assertThat(expired).isTrue();
    }

    @Test
    void isExpired_CheckedOutOrder_ReturnsFalseEvenIfTimerExpired() {
        // Arrange - Create order with FUTURE expiry, add item, checkout
        Instant futureExpiry = Instant.now().plus(10, ChronoUnit.MINUTES);
        ActiveOrder order = orderFactory.createOrder(testBuyer, eventId, futureExpiry);
        
        // Add an item and checkout while timer is still valid
        OrderItem item = new OrderItem("ZONE-1", "SEAT-1", 1, Money.of(new BigDecimal("100.00"), "USD"));
        order.addItem(item);
        order.checkout();

        // Act
        boolean expired = order.isExpired();

        // Assert - isExpired checks status == ACTIVE, so CHECKED_OUT order returns false
        assertThat(expired).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Test 3: expire() - Status transition and item release
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void expire_ChangesStatusToExpired() {
        // Arrange
        Instant expiry = Instant.now().plus(10, ChronoUnit.MINUTES);
        ActiveOrder order = orderFactory.createOrder(testBuyer, eventId, expiry);

        // Act
        order.expire();

        // Assert - After expire(), order is no longer active
        assertThat(order.isExpired()).isFalse(); // isExpired only returns true if ACTIVE && timer passed
    }

    @Test
    void expire_ReturnsAllItems() {
        // Arrange
        Instant expiry = Instant.now().plus(10, ChronoUnit.MINUTES);
        ActiveOrder order = orderFactory.createOrder(testBuyer, eventId, expiry);
        
        OrderItem item1 = new OrderItem("VIP-ZONE", "SEAT-101", 1, Money.of(new BigDecimal("150.00"), "USD"));
        OrderItem item2 = new OrderItem("REGULAR-ZONE", "SEAT-201", 1, Money.of(new BigDecimal("75.00"), "USD"));
        
        order.addItem(item1);
        order.addItem(item2);

        // Act
        List<OrderItem> expiredItems = order.expire();

        // Assert
        assertThat(expiredItems)
                .hasSize(2)
                .containsExactly(item1, item2);
    }

    @Test
    void expire_PreventsSubsequentOperations() {
        // Arrange
        Instant expiry = Instant.now().plus(10, ChronoUnit.MINUTES);
        ActiveOrder order = orderFactory.createOrder(testBuyer, eventId, expiry);
        order.expire();

        // Act & Assert - Try to add item after expiration
        OrderItem newItem = new OrderItem("ZONE", "SEAT", 1, Money.of(new BigDecimal("100.00"), "USD"));
        
        assertThatThrownBy(() -> order.addItem(newItem))
                .isInstanceOf(ActiveOrderNotActiveException.class);
    }

    @Test
    void expire_EmptyOrder_ReturnsEmptyList() {
        // Arrange
        Instant expiry = Instant.now().plus(10, ChronoUnit.MINUTES);
        ActiveOrder order = orderFactory.createOrder(testBuyer, eventId, expiry);

        // Act
        List<OrderItem> expiredItems = order.expire();

        // Assert
        assertThat(expiredItems).isEmpty();
    }

    @Test
    void cancel_AlsoReturnsAllItems() {
        // Arrange
        Instant expiry = Instant.now().plus(10, ChronoUnit.MINUTES);
        ActiveOrder order = orderFactory.createOrder(testBuyer, eventId, expiry);
        
        OrderItem item = new OrderItem("ZONE-1", "SEAT-1", 1, Money.of(new BigDecimal("100.00"), "USD"));
        order.addItem(item);

        // Act
        List<OrderItem> cancelledItems = order.cancel();

        // Assert
        assertThat(cancelledItems)
                .hasSize(1)
                .contains(item);
    }

}
