package com.eventsystem.infrastructure.persistence.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.IActiveOrderRepository;
import com.eventsystem.domain.order.OrderStatus;
import com.eventsystem.infrastructure.persistence.mapper.ActiveOrderMapper;

@Repository
public class ActiveOrderRepositoryImpl implements IActiveOrderRepository {

    private final JpaActiveOrderRepository jpaRepo;
    private final ActiveOrderMapper mapper;

    public ActiveOrderRepositoryImpl(JpaActiveOrderRepository jpaRepo,
                                     ActiveOrderMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    @Override
    public Optional<ActiveOrder> findById(String orderId) {
        return jpaRepo.findById(orderId)
                .map(e -> mapper.toDomain(e));
    }

    @Override
    public Optional<ActiveOrder> findByBuyerAndEvent(BuyerReference buyer, String eventId) {

        String buyerId = (buyer.memberId() != null)
                ? buyer.memberId()
                : buyer.sessionId();

        return jpaRepo.findByBuyerRef_MemberIdAndEventId(buyerId, eventId)
                .stream()
                .findFirst()
                .map(e -> mapper.toDomain(e));
    }

    @Override
    public Optional<List<ActiveOrder>> findExpired() {
        return Optional.of(
                jpaRepo.findByReservationExpiryBeforeAndStatus(
                                Instant.now(),
                                OrderStatus.ACTIVE
                        )
                        .stream()
                        .map(e -> mapper.toDomain(e))
                        .collect(Collectors.toList())
        );
    }

    @Override
    public void save(ActiveOrder order) {
        jpaRepo.save(mapper.toEntity(order));
    }

    @Override
    public void delete(String orderId) {
        jpaRepo.deleteById(orderId);
    }
}