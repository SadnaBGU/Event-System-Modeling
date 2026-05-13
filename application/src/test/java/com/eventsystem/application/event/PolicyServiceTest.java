package com.eventsystem.application.event;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UC16: Define Purchase and Discount Policies
 * 
 * Tests for UATs:
 * - UAT-44: Set purchase policy (max tickets per buyer)
 * - UAT-45: Set visible discount (early bird discount)
 * - UAT-46: Set coupon code (hidden discount code)
 * - UAT-47: Invalid coupon at checkout
 * - UAT-48: Policy conflict detected
 * - UAT-49: Modify policy with active orders
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    private EventQueryPort eventQueryPort;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private ActiveOrderRepository orderRepository;

    private PolicyService policyService;
    private EventId eventId;

    @BeforeEach
    void setUp() {
        eventId = new EventId("EVENT-POLICY-TEST");
        policyService = new PolicyService(policyRepository, eventQueryPort, orderRepository);
    }

    // ─────────────────────────────────────────────────────────────────────
    // UAT-44: Set Purchase Policy
    // Define max tickets per buyer for an event
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void setEventPurchasePolicy_Success() {
        // Arrange - UAT-44: Set purchase policy
        int maxTicketsPerBuyer = 4;
        PurchasePolicy purchasePolicy = new PurchasePolicy(
                eventId,
                maxTicketsPerBuyer,
                null, // no age restrictions for this test
                null  // no reserved seat limits
        );

        // Act
        policyService.setPurchasePolicy(eventId, purchasePolicy);

        // Assert
        verify(policyRepository, times(1)).savePurchasePolicy(purchasePolicy);
    }

    @Test
    void setEventPurchasePolicy_InvalidMaxTickets_ThrowsException() {
        // Arrange - UAT-44: Invalid policy data
        PurchasePolicy invalidPolicy = new PurchasePolicy(
                eventId,
                0, // Invalid: zero tickets
                null,
                null
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            policyService.setPurchasePolicy(eventId, invalidPolicy);
        });
        
        verify(policyRepository, never()).savePurchasePolicy(any());
    }

    // ─────────────────────────────────────────────────────────────────────
    // UAT-45: Set Visible Discount (Early Bird)
    // Create a discount visible to all customers
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void setVisibleDiscount_EarlyBirdDiscount_Success() {
        // Arrange - UAT-45: Set visible discount
        DiscountPolicy visibleDiscount = new DiscountPolicy(
                eventId,
                DiscountType.VISIBLE_PERCENTAGE,
                new BigDecimal("20"), // 20% off
                LocalDate.now().plusDays(1), // Valid until tomorrow
                "Early Bird Sale - 20% Off"
        );

        // Act
        policyService.setDiscountPolicy(eventId, visibleDiscount);

        // Assert
        verify(policyRepository, times(1)).saveDiscountPolicy(visibleDiscount);
    }

    @Test
    void setVisibleDiscount_ExpiredDate_ThrowsException() {
        // Arrange - UAT-45: Discount with invalid date
        DiscountPolicy expiredDiscount = new DiscountPolicy(
                eventId,
                DiscountType.VISIBLE_PERCENTAGE,
                new BigDecimal("20"),
                LocalDate.now().minusDays(1), // Already expired
                "Expired Discount"
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            policyService.setDiscountPolicy(eventId, expiredDiscount);
        });
        
        verify(policyRepository, never()).saveDiscountPolicy(any());
    }

    // ─────────────────────────────────────────────────────────────────────
    // UAT-46: Set Coupon Code
    // Create hidden discount code that only works with specific code
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void setCouponCode_CreateCouponDiscount_Success() {
        // Arrange - UAT-46: Set coupon code
        CouponCode coupon = new CouponCode(
                "DISCOUNT10",
                eventId,
                new BigDecimal("50"), // $50 off
                10, // Maximum 10 uses
                LocalDate.now().plusDays(30)
        );

        // Act
        policyService.createCouponCode(eventId, coupon);

        // Assert
        verify(policyRepository, times(1)).saveCouponCode(coupon);
    }

    @Test
    void setCouponCode_DuplicateCode_ThrowsException() {
        // Arrange - UAT-46: Duplicate coupon code
        CouponCode coupon = new CouponCode(
                "DUPLICATE",
                eventId,
                new BigDecimal("50"),
                10,
                LocalDate.now().plusDays(30)
        );

        when(policyRepository.findCouponCode("DUPLICATE", eventId))
                .thenReturn(java.util.Optional.of(coupon));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            policyService.createCouponCode(eventId, coupon);
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // UAT-47: Invalid Coupon at Checkout
    // Verify that invalid coupon codes are rejected at checkout
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void applyCoupon_NonExistentCode_Fails() {
        // Arrange - UAT-47: Invalid coupon at checkout
        String invalidCode = "NONEXISTENT";
        
        when(policyRepository.findCouponCode(invalidCode, eventId))
                .thenReturn(java.util.Optional.empty());

        // Act & Assert
        DiscountSnapshot result = policyService.validateAndApplyCoupon(eventId, invalidCode, new BigDecimal("100.00"));

        // Should return no discount for invalid code
        assertEquals(BigDecimal.ZERO, result.discountAmount());
    }

    @Test
    void applyCoupon_ExpiredCode_Fails() {
        // Arrange - UAT-47: Expired coupon code
        CouponCode expiredCoupon = new CouponCode(
                "EXPIRED",
                eventId,
                new BigDecimal("50"),
                5,
                LocalDate.now().minusDays(1) // Already expired
        );

        when(policyRepository.findCouponCode("EXPIRED", eventId))
                .thenReturn(java.util.Optional.of(expiredCoupon));

        // Act & Assert
        DiscountSnapshot result = policyService.validateAndApplyCoupon(eventId, "EXPIRED", new BigDecimal("100.00"));

        // Expired coupon should be rejected
        assertEquals(BigDecimal.ZERO, result.discountAmount());
    }

    @Test
    void applyCoupon_ExhaustedUses_Fails() {
        // Arrange - UAT-47: Coupon with zero remaining uses
        CouponCode exhaustedCoupon = new CouponCode(
                "EXHAUSTED",
                eventId,
                new BigDecimal("50"),
                0, // No uses remaining
                LocalDate.now().plusDays(30)
        );

        when(policyRepository.findCouponCode("EXHAUSTED", eventId))
                .thenReturn(java.util.Optional.of(exhaustedCoupon));

        // Act & Assert
        DiscountSnapshot result = policyService.validateAndApplyCoupon(eventId, "EXHAUSTED", new BigDecimal("100.00"));

        // Exhausted coupon should be rejected
        assertEquals(BigDecimal.ZERO, result.discountAmount());
    }

    // ─────────────────────────────────────────────────────────────────────
    // UAT-48: Policy Conflict Detected
    // Verify that conflicting policies are rejected
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void setPurchasePolicy_ConflictDetected_ThrowsException() {
        // Arrange - UAT-48: Policy conflict
        // Scenario: "buy 5 get 1 free" discount but purchase policy limits to 4 tickets
        DiscountPolicy discount = new DiscountPolicy(
                eventId,
                DiscountType.CONDITIONAL_BULK,
                new BigDecimal("5"), // Buy 5, get 1 free
                LocalDate.now().plusDays(30),
                "Buy 5 Get 1 Free"
        );
        
        PurchasePolicy purchasePolicy = new PurchasePolicy(
                eventId,
                4, // Max 4 tickets per buyer (conflicts with "buy 5 get 1 free")
                null,
                null
        );

        when(policyRepository.getDiscountPolicy(eventId))
                .thenReturn(java.util.Optional.of(discount));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            policyService.validateAndSetPurchasePolicy(eventId, purchasePolicy);
        });

        assertEquals("Policy conflict: Bulk discount requires 5+ tickets but purchase limit is 4", 
                exception.getMessage());
    }

    @Test
    void setDiscountPolicy_ConflictWithPurchasePolicy_ThrowsException() {
        // Arrange - UAT-48: Discount conflicts with existing purchase policy
        PurchasePolicy existingPolicy = new PurchasePolicy(
                eventId,
                3, // Max 3 tickets
                null,
                null
        );
        
        DiscountPolicy conflictingDiscount = new DiscountPolicy(
                eventId,
                DiscountType.CONDITIONAL_BULK,
                new BigDecimal("4"), // Buy 4 get discount (conflicts with 3-ticket max)
                LocalDate.now().plusDays(30),
                "Buy 4 Get Discount"
        );

        when(policyRepository.getPurchasePolicy(eventId))
                .thenReturn(java.util.Optional.of(existingPolicy));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            policyService.validateAndSetDiscountPolicy(eventId, conflictingDiscount);
        });

        assertEquals("Policy conflict: Discount requires 4 tickets minimum but purchase limit is 3",
                exception.getMessage());
    }

    // ─────────────────────────────────────────────────────────────────────
    // UAT-49: Modify Policy with Active Orders
    // Verify that changing policy while orders are active warns user
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void modifyPurchasePolicy_WithActiveOrders_EmitsWarning() {
        // Arrange - UAT-49: Modify policy with active orders
        // Simulate 3 active orders for this event
        when(orderRepository.findActiveOrdersByEvent(eventId))
                .thenReturn(java.util.List.of(
                        com.eventsystem.domain.order.ActiveOrder.createDummy(),
                        com.eventsystem.domain.order.ActiveOrder.createDummy(),
                        com.eventsystem.domain.order.ActiveOrder.createDummy()
                ));

        PurchasePolicy newPolicy = new PurchasePolicy(
                eventId,
                2, // Reducing from 4 to 2
                null,
                null
        );

        // Act
        PolicyModificationWarning warning = policyService.modifyPurchasePolicy(eventId, newPolicy);

        // Assert
        assertTrue(warning.hasWarning());
        assertEquals("Policy change will only apply to future orders. 3 active orders will not be affected.", 
                warning.getMessage());
        
        // Policy should still be saved
        verify(policyRepository, times(1)).savePurchasePolicy(newPolicy);
    }

    @Test
    void modifyPurchasePolicy_NoActiveOrders_NoWarning() {
        // Arrange - UAT-49: Modify policy without active orders
        when(orderRepository.findActiveOrdersByEvent(eventId))
                .thenReturn(java.util.List.of()); // No active orders

        PurchasePolicy newPolicy = new PurchasePolicy(
                eventId,
                2,
                null,
                null
        );

        // Act
        PolicyModificationWarning warning = policyService.modifyPurchasePolicy(eventId, newPolicy);

        // Assert
        assertFalse(warning.hasWarning());
        
        // Policy should be saved
        verify(policyRepository, times(1)).savePurchasePolicy(newPolicy);
    }

    @Test
    void modifyDiscountPolicy_WithActiveOrders_RequiresConfirmation() {
        // Arrange - UAT-49: Modify discount while orders active
        when(orderRepository.findActiveOrdersByEvent(eventId))
                .thenReturn(java.util.List.of(
                        com.eventsystem.domain.order.ActiveOrder.createDummy()
                ));

        DiscountPolicy newDiscount = new DiscountPolicy(
                eventId,
                DiscountType.VISIBLE_PERCENTAGE,
                new BigDecimal("30"), // Increased from 20%
                LocalDate.now().plusDays(30),
                "New Discount"
        );

        // Act
        PolicyModificationWarning warning = policyService.modifyDiscountPolicy(eventId, newDiscount);

        // Assert - Should emit warning but still allow change
        assertTrue(warning.hasWarning());
        assertEquals("1 active order will continue with previous discount terms.", warning.getMessage());
    }

}