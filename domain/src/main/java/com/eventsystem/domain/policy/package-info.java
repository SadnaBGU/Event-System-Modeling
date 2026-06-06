/**
 * Policy domain model.
 *
 * <p>Root package of the Policy bounded context. It contains the shared
 * policy construction and analysis services. Concrete policy aggregates,
 * rules, and shared value objects are organized in subpackages.</p>
 *
 * <p>Package contents:</p>
 * <ul>
 *   <li>{@code purchase}: purchase-policy aggregate and identity;</li>
 *   <li>{@code discount}: discount-policy aggregate and discount-specific value objects;</li>
 *   <li>{@code rule}: reusable policy-rule language, including basic and composite rules;</li>
 *   <li>{@code shared}: shared policy value objects, evaluation context, and validation results;</li>
 *   <li>{@link PolicyBuilder}: factory/helper for building complex policy rule trees;</li>
 *   <li>{@link PolicyConflictDetector}: domain service for detecting internal rule conflicts
 *       and conflicts between purchase and discount policies.</li>
 * </ul>
 *
 * <p>To add a new policy condition, start in {@code rule}. If the rule should be
 * built from application/API commands, also expose it through {@link PolicyBuilder}
 * and the application policy command assembler.</p>
 *
 * @see com.eventsystem.domain.policy.shared.PurchaseContext
 * @see com.eventsystem.domain.policy.shared.PolicyValidationResult
 * @see com.eventsystem.domain.policy.rule.IPolicy
 */
package com.eventsystem.domain.policy;