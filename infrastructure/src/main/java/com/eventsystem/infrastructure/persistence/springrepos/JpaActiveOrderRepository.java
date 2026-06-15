package com.eventsystem.infrastructure.persistence.springrepos;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eventsystem.infrastructure.persistence.entities.ActiveOrderEntity;

public interface JpaActiveOrderRepository
        extends JpaRepository<ActiveOrderEntity, String> {

    List<ActiveOrderEntity> findByBuyerRef_MemberIdAndEventId(String memberId, String eventId);

    List<ActiveOrderEntity> findByReservationExpiryBeforeAndStatus(
            Instant now,
            com.eventsystem.domain.order.OrderStatus status
    );
}