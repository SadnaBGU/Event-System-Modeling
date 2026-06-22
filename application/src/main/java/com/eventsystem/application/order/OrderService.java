package com.eventsystem.application.order;

import com.eventsystem.application.appexceptions.AlreadyExistsOrderException;
import com.eventsystem.application.appexceptions.OrderNotFoundException;
import com.eventsystem.application.appexceptions.ZoneApplicationException;
import com.eventsystem.domain.domainexceptions.ZoneDomainException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.ILotteryRepository;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.IActiveOrderRepository;
import com.eventsystem.domain.order.OrderFactory;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.order.OrderStatus;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.ZoneType;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final IActiveOrderRepository orderRepository;
    private final IZoneRepository zoneRepository;
    private final OrderFactory orderFactory;
    private final ILotteryRepository lotteryRepository;
    
    private static final int TIMEOUT_MINUTES = 10; 

    public OrderService(IActiveOrderRepository orderRepository, IZoneRepository zoneRepository, OrderFactory orderFactory, ILotteryRepository lotteryRepository) {
        this.orderRepository = orderRepository;
        this.zoneRepository = zoneRepository;
        this.orderFactory = orderFactory;
        this.lotteryRepository = lotteryRepository;
    }

    /**
     * Create a new active order for the buyer and event, 
     * or return the existing active order if it exists and is not expired.
     */
    public ActiveOrderDTO createOrGetActiveOrder(BuyerReference buyer, String eventId, Optional<String> lotteryCode) {
        logger.info("Requested active order for event");

        Optional<Lottery> lottery = lotteryRepository.findByEventId(new EventId(eventId));

        if (lotteryCode.isPresent() && lottery.isPresent()) {
            Optional<MemberId> memberId = lottery.get().validateCode(lotteryCode.get(), Clock.systemUTC().instant());
            if (!memberId.isPresent()) {
                throw new SecurityException("Invalid lottery code provided");
            }
            if (!memberId.get().value().equals(buyer.memberId())) {
                throw new SecurityException("Lottery code does not belong to the buyer");
            }
        }

        Optional<ActiveOrder> existingOrder = orderRepository.findByBuyerAndEvent(buyer, eventId);
        
        if (existingOrder.isPresent()
                && existingOrder.get().getStatus() == OrderStatus.ACTIVE
                && !existingOrder.get().isExpired()) {
            logger.info("Found existing active order: {}", existingOrder.get().getOrderId());
            return ActiveOrderDTO.fromDomain(existingOrder.get());
        }

        ActiveOrder newOrder = orderFactory.createOrder(
                buyer, 
                eventId, 
                Instant.now().plus(TIMEOUT_MINUTES, ChronoUnit.MINUTES)
        );
        orderRepository.save(newOrder);
        logger.info("Created new active order: {}", newOrder.getOrderId());
        return ActiveOrderDTO.fromDomain(newOrder);
    }

    public ActiveOrderDTO createNewOrderStrict(BuyerReference buyer, String eventId) {
        Optional<ActiveOrder> existingOrder = orderRepository.findByBuyerAndEvent(buyer, eventId);
        
        if (existingOrder.isPresent()
                && existingOrder.get().getStatus() == OrderStatus.ACTIVE
                && !existingOrder.get().isExpired()) {
            logger.warn("Reservation Rejected_Existing_Order: Buyer already has active order for event {}", eventId);
            throw new AlreadyExistsOrderException("An active order for this buyer and event already exists.");
        }

        ActiveOrder newOrder = orderFactory.createOrder(
                buyer, 
                eventId, 
                Instant.now().plus(TIMEOUT_MINUTES, ChronoUnit.MINUTES)
        );
        orderRepository.save(newOrder);
        return ActiveOrderDTO.fromDomain(newOrder);
    }

    /**
     * Add a seat to the active order. This involves:
     * 1. Locking the seat through the ZoneRepository.
     * 2. If the lock is successful, adding the item to our order aggregate and saving it.
     *
     * For standing zones (no per-seat identity), the seatId is treated as a label only and
     * the zone is decremented by 1 via reserveStanding.
     */
    public void addItemToOrder(String orderId, String zoneId, String seatId, int quantity) {
        logger.info("Attempting to add item {} in zone {} for order {}", seatId, zoneId, orderId);

        ActiveOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    logger.warn("Failed to reserve seat: Order {} not found", orderId);
                    return new OrderNotFoundException("Active order " + orderId + " not found");
                });

        try {
            final OrderItem[] itemHolder = new OrderItem[1];
            ZoneId zId = new ZoneId(zoneId);

            zoneRepository.withLock(zId, () -> {
                Zone zone = zoneRepository.findById(zId)
                        .orElseThrow(() -> new ZoneDomainException("Zone not found"));

                if (zone.zoneType() == ZoneType.STANDING) {
                    zone.reserveStanding(quantity);
                } else {
                    zone.reserveSeat(new SeatId(seatId));
                }
                zoneRepository.save(zone);

                itemHolder[0] = new OrderItem(zone.zoneId().value(), seatId, quantity, zone.pricePerTicket());
            });

            // step 2: if the lock is successful, add the item to our order aggregate and save it
            order.addItem(itemHolder[0]);
            orderRepository.save(order);
            
            logger.info("Successfully reserved seat {} for order {}", seatId, orderId);
            
        } catch (ZoneDomainException e) {
            logger.warn("Failed to reserve seat {} for order {}: {}", seatId, orderId, e.getMessage());
            throw new ZoneApplicationException("Failed to reserve seat: " + e.getMessage());
        } catch (Exception e) {
            logger.error("System error while attempting to reserve seat {} for order {}", seatId, orderId, e);
            throw new RuntimeException("Unexpected error occurred", e);
        }
    }

    /**
     * Release a seat from the active order. This involves:
     * 1. Removing the item from our order aggregate and saving it.
     * 2. Unlocking the seat through the ZoneRepository to make it available again.
     *
     * For standing zones, increments the available count by 1 via releaseStanding.
     */
    public void removeItemFromOrder(String orderId, String zoneId, String seatId, int quantity) {
        logger.info("Attempting to remove item {} in zone {} from order {}", seatId, zoneId, orderId);
        ActiveOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    logger.warn("Failed to release seat: Order {} not found", orderId);
                    return new OrderNotFoundException("Active order not found");
                });

        // release the seat from the order aggregate and save it
        order.removeItem(zoneId, seatId);
        orderRepository.save(order);

        // unlock the seat through the ZoneRepository to make it available again
        ZoneId zId = new ZoneId(zoneId);
        zoneRepository.withLock(zId, () -> {
            Zone zone = zoneRepository.findById(zId)
                    .orElseThrow(() -> new ZoneDomainException("Zone not found"));

            if (zone.zoneType() == ZoneType.STANDING) {
                zone.releaseStanding(quantity);
            } else {
                zone.releaseSeat(new SeatId(seatId));
            }
            zoneRepository.save(zone);
        });
        
        logger.info("Successfully released seat {} and unlocked it in zone {}", seatId, zoneId);
    }

    /**
     * This method should be called periodically (e.g., every minute) 
     * to find and expire any active orders that have passed their expiration time.
     */
    public void sweepExpiredOrders() {
        logger.info("Starting background sweep for expired orders");
        List<ActiveOrder> expiredOrders = orderRepository.findExpired()
                .orElseThrow(() -> {
                    logger.error("Failed to fetch expired orders from repository");
                    return new IllegalStateException("Error fetching expired orders");
                });
        
        if (expiredOrders.isEmpty()) {
            logger.info("No expired orders found during sweep");
            return;
        }
        
        int count = 0;
        for (ActiveOrder order : expiredOrders) {
            // Isolate each order: a failure releasing one order's inventory must not
            // abort the whole recovery batch, so the next order still gets swept.
            try {
                List<OrderItem> expiredItems = order.expire();
                orderRepository.save(order);

                for (OrderItem item : expiredItems) {
                    ZoneId zId = new ZoneId(item.getZoneId());
                    zoneRepository.withLock(zId, () -> {
                        zoneRepository.findById(zId).ifPresent(zone -> {
                            if (zone.zoneType() == com.eventsystem.domain.zone.ZoneType.STANDING) {
                                zone.releaseStanding(item.getQuantity());
                            } else {
                                zone.releaseSeat(new SeatId(item.getSeatId()));
                            }
                            zoneRepository.save(zone);
                        });
                    });
                }
                logger.info("Order {} expired. Unlocked {} associated seats.", order.getOrderId(), expiredItems.size());
                count++;
            } catch (RuntimeException e) {
                logger.error("Failed to sweep expired order {}; continuing with remaining orders", order.getOrderId(), e);
            }
        }
        logger.info("Completed background sweep. Total expired orders processed: {}", count);
    }

    /**
     * Return the ActiveOrderDTO for a given orderId or throw OrderNotFoundException
     */
    public ActiveOrderDTO getOrderById(String orderId) {
        ActiveOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Active order " + orderId + " not found"));
        return ActiveOrderDTO.fromDomain(order);
    }
}