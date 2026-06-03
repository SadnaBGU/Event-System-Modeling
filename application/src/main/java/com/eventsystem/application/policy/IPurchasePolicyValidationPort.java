package com.eventsystem.application.policy;

import java.util.List;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;

/**
 * Handles purchase-policy validation and evaluation for a given purchase context.
 *
 * <p>Main methods:</p>
 * <ul>
 *     <li>{@code requirePurchasePolicyFor()} - evaluates purchase policies and throws if validation fails.</li>
 *     <li>{@code validatePurchasePolicyFor()} - evaluates purchase policies and returns true/false.</li>
 *     <li>{@code evaluatePurchasePolicyFor()} - evaluates purchase policies and returns a detailed result.</li>
 * </ul>
 *
 * @see PurchaseContext
 * @see PolicyValidationResult
 */
public interface IPurchasePolicyValidationPort {

    void requirePurchasePolicyFor(PurchaseContext context);

    boolean validatePurchasePolicyFor(PurchaseContext context);

    PolicyValidationResult evaluatePurchasePolicyFor(PurchaseContext context);

    PurchaseContext createPurchaseContext(EventId eventId, BuyerReference buyerRef, List<OrderItem> items);

    /**
     * Temporary compatibility method for the existing purchase/order flow.
     *
     * <p>New code should use {@link #validatePurchasePolicyFor(PurchaseContext)}
     * or {@link #evaluatePurchasePolicyFor(PurchaseContext)} instead.</p>
     *
     * @deprecated use {@link #validatePurchasePolicyFor(PurchaseContext)}
     * or {@link #evaluatePurchasePolicyFor(PurchaseContext)} instead.
     */
    @Deprecated
    boolean validatePurchasePolicy(String eventId, BuyerReference buyer, List<OrderItem> items);


}