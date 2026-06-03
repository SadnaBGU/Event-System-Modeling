/**
 * policy condition rules.
 *
 * <p>Defines policy rules used by both {@code DiscountPolicy} and {@code PurchasePolicy}.</p>
  * <p>Every rule implements {@link IPolicy} and is classified by {@link PolicyType}.</p>
 * <p>Single-condition rules belong under {@code basic}. Rules that combine or
 * scope other rules belong under {@code composite}.</p>
 *
 * <p>To add a new policy condition, implement {@link IPolicy} (or {@code IBasicPolicy} or {@code ICompositePolicy} ), classify it with
 * {@link PolicyType}, and expose construction through
 * {@link com.eventsystem.domain.policy.PolicyBuilder} when needed by application
 * or API commands.</p>
 *
 * @see com.eventsystem.domain.policy.rule.IPolicy
 * @see com.eventsystem.domain.policy.rule.PolicyType
 * @see com.eventsystem.domain.policy.shared.PurchaseContext
 * @see com.eventsystem.domain.policy.shared.PolicyValidationResult
 */
package com.eventsystem.domain.policy.rule;