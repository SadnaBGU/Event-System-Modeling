/**
 * Policy domain model.
 *
 * <p>Owns: {@code PurchasePolicy} + {@code DiscountPolicy} + {@code Ipolicy} + {@code Discount} +{@Code PolicyBuilder}
 * <p> {@code PurchasePolicy} + {@code DiscountPolicy}: Aggregate roots for Discount Policies and Purchase Policies.
 * <p> {@code IPolicy} Interface: policy simply checks conditions, and can be built of multiple condition checks.
 * basic folder contains single check polices, using Strategy Pattern, composite folder contains logical and scoped rules using Composition.
 * <p> {@code PolicyBuilder} : uses Builder for ease of constructing policies.
 *
 * Used by purchase-flow services during checkout.
 *
 * <p> **Concrete policies live in subpackages such as {@code basic} and {@code composite}.
 * <p> **To add policies: Create relevant class that implements IPolicy, for ease of use add it to {@code PolicyBuilder}
 *
 * <p>See: {@code docs/6_Policies.mmd}.
 */
package com.eventsystem.domain.policy;
