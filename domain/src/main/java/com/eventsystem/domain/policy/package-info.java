/**
 * Policy domain model.
 *
 * <p>Owns: {@code PurchasePolicy} + {@code DiscountPolicy} interfaces and their concrete
 * strategy implementations and {@code PolicyBuilder} for composing concrete policy rules to be used in those.
 *
 * Used by purchase-flow services during checkout.
 *
 * <p>Concrete rules live in subpackages such as {@code basic} and {@code composite}.
 *
 * <p>See: {@code docs/6_Policies.mmd}.
 */
package com.eventsystem.domain.policy;
