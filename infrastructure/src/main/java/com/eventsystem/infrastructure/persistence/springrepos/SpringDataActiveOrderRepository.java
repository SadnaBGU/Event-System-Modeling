package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
            select o
            from ActiveOrder o
            where o.eventId = :eventId
              and o.buyerRef.type = :buyerType
              and (
                    (o.buyerRef.sessionId is null and :sessionId is null)
                    or o.buyerRef.sessionId = :sessionId
                  )
              and (
                    (o.buyerRef.memberId is null and :memberId is null)
                    or o.buyerRef.memberId = :memberId
                  )
            """)
    Optional<ActiveOrder> findByBuyerAndEvent(
            @Param("buyerType") BuyerType buyerType,
            @Param("sessionId") String sessionId,
            @Param("memberId") String memberId,
            @Param("eventId") String eventId);

}
