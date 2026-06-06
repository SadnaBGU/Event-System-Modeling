/**
 * Discount-policy domain objects.
 *
 * <p>Contains the Discount-Policy aggregate, its identity, and discount-specific
 * value objects used to describe discount calculation, visibility, and public
 * discount summaries.</p>
 *
 * <p>Discount applicability conditions are not defined here. They are reusable
 * policy rules and belong under {@code rule}.</p>
 *
 * @see com.eventsystem.domain.policy.discount.DiscountPolicy
 * @see com.eventsystem.domain.policy.discount.Discount
 * @see com.eventsystem.domain.policy.discount.DiscountVisibility
 * @see com.eventsystem.domain.policy.shared.PolicyScope
 */
package com.eventsystem.domain.policy.discount;