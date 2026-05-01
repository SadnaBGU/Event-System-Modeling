/**
 * ActiveOrder aggregate.
 *
 * <p>Owns: {@code ActiveOrder} aggregate root + {@code OrderItem} entity +
 * {@code BuyerReference} VO + {@code OrderStatus} + {@code ActiveOrderRepository} port +
 * {@code OrderFactory} domain service.
 *
 * <p>See: {@code docs/4_ActiveOrder.mmd}.
 */
package com.eventsystem.domain.order;
