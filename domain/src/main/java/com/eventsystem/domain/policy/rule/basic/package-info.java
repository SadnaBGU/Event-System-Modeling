/**
 * Basic policy rules.
 * 
 * <p>Every basic rule implements {@link IBasicPolicy} </p>
 * <p>Contains leaf rules that check one business condition against a purchase
 * context, such as ticket quantity, buyer age, date boundaries, coupon code, or
 * default allow/deny behavior.</p>
 *
 * @see com.eventsystem.domain.policy.rule.basic.IBasicPolicy
 * @see com.eventsystem.domain.policy.rule.IPolicy
 * @see com.eventsystem.domain.policy.rule.PolicyType
 * @see com.eventsystem.domain.policy.shared.PurchaseContext
 */
package com.eventsystem.domain.policy.rule.basic;