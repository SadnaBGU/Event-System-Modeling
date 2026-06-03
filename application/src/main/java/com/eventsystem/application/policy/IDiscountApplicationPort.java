package com.eventsystem.application.policy;

import java.util.List;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.policy.discount.DiscountSummary;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.shared.Money;

/**
 * Handles discount-policy evaluation and application for a given purchase context.
 *
 * <p>Main methods:</p>
 * <ul>
 *     <li>{@code doesDiscountApplyFor()} - checks whether at least one active discount applies.</li>
 *     <li>{@code calculateDiscountSummary()} - calculates the discount result for the given context.</li>
 *     <li>{@code generateDiscountSnapshot()} - creates a snapshot of the applied discount for purchase records.</li>
 * </ul>
 *
 * @see PurchaseContext
 * @see DiscountSummary
 * @see DiscountSnapshot
 */

public interface IDiscountApplicationPort {

    boolean doesDiscountApplyFor(PurchaseContext context);

    DiscountSummary calculateDiscountSummary(PurchaseContext context, Money baseCost);

    DiscountSnapshot generateDiscountSnapshot(PurchaseContext context, Money baseTotal);

    List<EventId> getEventIdsWithActiveVisibleDiscounts();

    PurchaseContext createPurchaseContext(EventId eventId, BuyerReference buyerRef, List<OrderItem> items, String discountCode);

    @Deprecated
    DiscountSnapshot applyDiscount(String eventId, String discountCode, Money baseTotal);
}
