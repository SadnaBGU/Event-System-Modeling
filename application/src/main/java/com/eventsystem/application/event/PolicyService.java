package com.eventsystem.application.event;

import com.eventsystem.application.order.ActiveOrderRepository;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.CouponCode;
import com.eventsystem.domain.policy.DiscountPolicy;
import com.eventsystem.domain.policy.DiscountType;
import com.eventsystem.domain.policy.PurchasePolicy;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * PolicyService Application Service
 * Orchestrates policy creation, validation, and modification
 * Handles UATs 44-49
 */
public class PolicyService {
    private final PolicyRepository policyRepository;
    private final EventQueryPort eventQueryPort;
    private final ActiveOrderRepository activeOrderRepository;

    public PolicyService(PolicyRepository policyRepository, EventQueryPort eventQueryPort, ActiveOrderRepository activeOrderRepository) {
        this.policyRepository = policyRepository;
        this.eventQueryPort = eventQueryPort;
        this.activeOrderRepository = activeOrderRepository;
    }

    /**
     * UAT-44: Set Purchase Policy
     * Define maximum tickets per buyer and other purchase restrictions
     */
    public void setPurchasePolicy(EventId eventId, PurchasePolicy policy) {
        // Validate policy data
        if (policy.maxTicketsPerBuyer() <= 0) {
            throw new IllegalArgumentException("Max tickets per buyer must be positive");
        }
        policyRepository.savePurchasePolicy(policy);
    }

    /**
     * Validates and sets purchase policy with conflict checking
     */
    public void validateAndSetPurchasePolicy(EventId eventId, PurchasePolicy policy) {
        // Check for conflicts with existing discount policies
        Optional<DiscountPolicy> existingDiscount = policyRepository.getDiscountPolicy(eventId);
        
        if (existingDiscount.isPresent()) {
            DiscountPolicy discount = existingDiscount.get();
            
            // Check if bulk discount conflicts with purchase limit
            if (discount.type() == DiscountType.CONDITIONAL_BULK) {
                int bulkThreshold = discount.discountValue().intValue();
                if (policy.maxTicketsPerBuyer() < bulkThreshold) {
                    throw new IllegalStateException(
                            String.format("Policy conflict: Bulk discount requires %d+ tickets but purchase limit is %d",
                                    bulkThreshold, policy.maxTicketsPerBuyer())
                    );
                }
            }
        }
        
        setPurchasePolicy(eventId, policy);
    }

    /**
     * UAT-45: Set Visible Discount
     * Create discount visible to all customers (early bird, percentage off, etc.)
     */
    public void setDiscountPolicy(EventId eventId, DiscountPolicy policy) {
        // Validate discount data
        if (policy.validUntil().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Discount expiry date cannot be in the past");
        }
        policyRepository.saveDiscountPolicy(policy);
    }

    /**
     * Validates and sets discount policy with conflict checking
     */
    public void validateAndSetDiscountPolicy(EventId eventId, DiscountPolicy policy) {
        // Check for conflicts with existing purchase policies
        Optional<PurchasePolicy> existingPolicy = policyRepository.getPurchasePolicy(eventId);
        
        if (existingPolicy.isPresent()) {
            PurchasePolicy purchase = existingPolicy.get();
            
            // Check if bulk discount conflicts with purchase limit
            if (policy.type() == DiscountType.CONDITIONAL_BULK) {
                int bulkThreshold = policy.discountValue().intValue();
                if (purchase.maxTicketsPerBuyer() < bulkThreshold) {
                    throw new IllegalStateException(
                            String.format("Policy conflict: Discount requires %d tickets minimum but purchase limit is %d",
                                    bulkThreshold, purchase.maxTicketsPerBuyer())
                    );
                }
            }
        }
        
        setDiscountPolicy(eventId, policy);
    }

    /**
     * UAT-46: Set Coupon Code
     * Create hidden discount code that only works with specific coupon entry
     */
    public void createCouponCode(EventId eventId, CouponCode coupon) {
        // Check for duplicate coupon code
        Optional<CouponCode> existing = policyRepository.findCouponCode(coupon.code(), eventId);
        if (existing.isPresent()) {
            throw new IllegalStateException("Coupon code already exists: " + coupon.code());
        }
        
        policyRepository.saveCouponCode(coupon);
    }

    /**
     * UAT-47: Validate and Apply Coupon at Checkout
     * Verify coupon is valid and apply appropriate discount
     */
    public DiscountSnapshot validateAndApplyCoupon(EventId eventId, String couponCode, BigDecimal orderAmount) {
        Optional<CouponCode> coupon = policyRepository.findCouponCode(couponCode, eventId);
        
        // If coupon doesn't exist, return no discount
        if (coupon.isEmpty()) {
            return new DiscountSnapshot("Invalid coupon code", BigDecimal.ZERO);
        }
        
        CouponCode code = coupon.get();
        
        // Check if coupon is still valid
        if (code.isExpired()) {
            return new DiscountSnapshot("Coupon expired", BigDecimal.ZERO);
        }
        
        // Check if coupon has remaining uses
        if (!code.hasRemainingUses()) {
            return new DiscountSnapshot("Coupon exhausted", BigDecimal.ZERO);
        }
        
        // Apply discount
        BigDecimal discount = code.discountAmount();
        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount; // Cap discount at order amount
        }
        
        return new DiscountSnapshot(couponCode + " applied", discount);
    }

    /**
     * UAT-48: Detect Policy Conflicts
     * Verify that new policies don't conflict with existing rules
     */
    public boolean hasConflict(EventId eventId, PurchasePolicy purchasePolicy, DiscountPolicy discountPolicy) {
        // Check bulk discount vs purchase limit
        if (discountPolicy.type() == DiscountType.CONDITIONAL_BULK) {
            int bulkThreshold = discountPolicy.discountValue().intValue();
            return purchasePolicy.maxTicketsPerBuyer() < bulkThreshold;
        }
        
        return false;
    }

    /**
     * UAT-49: Modify Policy with Active Orders
     * Allow policy modification but warn about impact on active orders
     */
    public PolicyModificationWarning modifyPurchasePolicy(EventId eventId, PurchasePolicy newPolicy) {
        // Find active orders for this event
        var activeOrders = activeOrderRepository.findActiveOrdersByEvent(eventId);
        
        if (!activeOrders.isEmpty()) {
            String warning = String.format(
                    "Policy change will only apply to future orders. %d active order(s) will not be affected.",
                    activeOrders.size()
            );
            policyRepository.savePurchasePolicy(newPolicy);
            return PolicyModificationWarning.warning(warning);
        }
        
        policyRepository.savePurchasePolicy(newPolicy);
        return PolicyModificationWarning.noWarning();
    }

    /**
     * UAT-49: Modify Discount Policy with Active Orders
     * Warn user about discount changes affecting future orders only
     */
    public PolicyModificationWarning modifyDiscountPolicy(EventId eventId, DiscountPolicy newPolicy) {
        // Find active orders for this event
        var activeOrders = activeOrderRepository.findActiveOrdersByEvent(eventId);
        
        if (!activeOrders.isEmpty()) {
            String warning = String.format(
                    "%d active order will continue with previous discount terms.",
                    activeOrders.size()
            );
            policyRepository.saveDiscountPolicy(newPolicy);
            return PolicyModificationWarning.warning(warning);
        }
        
        policyRepository.saveDiscountPolicy(newPolicy);
        return PolicyModificationWarning.noWarning();
    }
}
