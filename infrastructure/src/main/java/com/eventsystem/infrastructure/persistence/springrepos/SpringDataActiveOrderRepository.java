package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SpringDataActiveOrderRepository extends JpaRepository<ActiveOrder, String> {

    // Find all active orders for a specific event
    List<ActiveOrder> findByEventId(String eventId);

    // Find orders by their workflow status
    List<ActiveOrder> findByStatus(OrderStatus status);

    // Useful for a background job/cron to find and clean up expired reservations
    List<ActiveOrder> findByStatusAndReservationExpiryBefore(OrderStatus status, Instant now);

    Optional<ActiveOrder> findByBuyerRefAndEventId(BuyerReference buyerRef, String eventId);


    List<ActiveOrder> findByReservationExpiryBefore(Instant now);


}
