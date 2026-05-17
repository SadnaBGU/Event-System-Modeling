package com.eventsystem.application.order;

import com.eventsystem.application.appexceptions.AlreadyExistsOrderException;
import com.eventsystem.application.appexceptions.OrderNotFoundException;
import com.eventsystem.application.appexceptions.ZoneApplicationException;
import com.eventsystem.application.event.ZoneServicePort;
import com.eventsystem.application.lottery.ILotteryValidationPort;
import com.eventsystem.domain.domainexceptions.ZoneDomainException;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderFactory;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.ZoneId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final IActiveOrderRepository orderRepository;
    private final ZoneServicePort zoneService;
    private final OrderFactory orderFactory;
    private final ILotteryValidationPort lotteryValidationPort;
    
    private static final int TIMEOUT_MINUTES = 10; 

    public OrderService(IActiveOrderRepository orderRepository, ZoneServicePort zoneService, OrderFactory orderFactory, ILotteryValidationPort lotteryValidationPort) {
        this.orderRepository = orderRepository;
        this.zoneService = zoneService;
        this.orderFactory = orderFactory;
        this.lotteryValidationPort = lotteryValidationPort;
    }

    /**
     * Create a new active order for the buyer and event, 
     * or return the existing active order if it exists and is not expired.
     */
    public ActiveOrderDTO createOrGetActiveOrder(BuyerReference buyer, String eventId, Optional<String> lotteryCode) {
        logger.info("Requested active order for event");

        if (lotteryCode.isPresent() && lotteryValidationPort.isLotteryEvent(eventId)) {
            boolean isValid = lotteryValidationPort.validateWinnerCode(eventId, buyer, lotteryCode.get());
            if (!isValid) {
                throw new SecurityException("Lottery authorization failed. Access denied.");
            }
        }

        Optional<ActiveOrder> existingOrder = orderRepository.findByBuyerAndEvent(buyer, eventId);
        
        if (existingOrder.isPresent() && !existingOrder.get().isExpired()) {
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
        
        if (existingOrder.isPresent() && !existingOrder.get().isExpired()) {
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
     * 1. Locking the seat through the ZoneService (which will throw an exception if the seat is already taken).
     * 2. If the lock is successful, adding the item to our order aggregate and saving it.
     */
    public void reserveSeat(String orderId, String zoneId, String seatId) {
        logger.info("Attempting to reserve seat {} in zone {} for order {}", seatId, zoneId, orderId);

        ActiveOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    logger.warn("Failed to reserve seat: Order {} not found", orderId);
                    return new OrderNotFoundException("Active order" + orderId + "not found");
                });

        try{
            // step 1: try to lock the seat through the ZoneService
            OrderItem lockedItem = zoneService.reserveSeat(new ZoneId(zoneId), new SeatId(seatId));

            // step 2: if the lock is successful, add the item to our order aggregate and save it
            order.addItem(lockedItem);
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
     * 2. Unlocking the seat through the ZoneService to make it available again.
     */
    public void releaseSeat(String orderId, String zoneId, String seatId) {
        logger.info("Attempting to release seat {} from order {}", seatId, orderId);
        ActiveOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    logger.warn("Failed to release seat: Order {} not found", orderId);
                    return new OrderNotFoundException("Active order not found");
                });

        // release the seat from the order aggregate and save it
        order.removeItem(zoneId, seatId);
        orderRepository.save(order);

        // unlock the seat through the ZoneService to make it available again
        zoneService.releaseSeat(new ZoneId(zoneId), new SeatId(seatId));
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
            List<OrderItem> expiredItems = order.expire();
            orderRepository.save(order);
            for (OrderItem item : expiredItems) {
                zoneService.releaseSeat(new ZoneId(item.getZoneId()), new SeatId(item.getSeatId()));
            }
            logger.info("Order {} expired. Unlocked {} associated seats.", order.getOrderId(), expiredItems.size());
            count++;
        }
        logger.info("Completed background sweep. Total expired orders processed: {}", count);
    }
}