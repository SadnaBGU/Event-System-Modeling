/**
 * Composite policy rules.
 *
 * <p>Contains rules that combine, decorate, or scope other policy rules, such as
 * logical AND, logical OR, and zone-specific policy evaluation.</p>
 *
 * <p>Every composite rule implements {@link ICompositePolicy} and is built from
 * other policy rules.</p>
 *
 * @see com.eventsystem.domain.policy.rule.composite.ICompositePolicy
 * @see com.eventsystem.domain.policy.rule.IPolicy
 * @see com.eventsystem.domain.policy.rule.PolicyType
 * @see com.eventsystem.domain.policy.shared.PurchaseContext
 */
package com.eventsystem.domain.policy.rule.composite;