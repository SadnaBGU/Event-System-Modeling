/**
 * Purchase-policy domain objects.
 *
 * <p>Contains the Purchase-Policy aggregate and its identity. A purchase policy
 * decides whether a buyer may complete checkout for a given purchase context.</p>
 *
 * <p>The actual purchase restrictions are expressed using reusable policy rules
 * from {@code rule}, and evaluation results are represented by shared validation
 * objects from {@code shared}.</p>
 *
 * @see com.eventsystem.domain.policy.purchase.PurchasePolicy
 * @see com.eventsystem.domain.policy.shared.PurchaseContext
 * @see com.eventsystem.domain.policy.shared.PolicyValidationResult
 * @see com.eventsystem.domain.policy.rule.IPolicy
 */
package com.eventsystem.domain.policy.purchase;