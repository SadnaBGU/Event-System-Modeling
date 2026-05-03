package com.eventsystem.application.order;

import com.eventsystem.application.event.ZoneServicePort;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderFactory;
import com.eventsystem.domain.order.OrderItem;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

public class OrderService {

    private final ActiveOrderRepository orderRepository;
    private final ZoneServicePort zoneService;
    private final OrderFactory orderFactory;
    
    private static final int TIMEOUT_MINUTES = 10; 

    public OrderService(ActiveOrderRepository orderRepository, ZoneServicePort zoneService, OrderFactory orderFactory) {
        this.orderRepository = orderRepository;
        this.zoneService = zoneService;
        this.orderFactory = orderFactory;
    }

    /**
     * Create a new active order for the buyer and event, 
     * or return the existing active order if it exists and is not expired.
     */
    public String createOrGetActiveOrder(BuyerReference buyer, String eventId) {
        Optional<ActiveOrder> existingOrder = orderRepository.findByBuyerAndEvent(buyer, eventId);
        
        if (existingOrder.isPresent() && !existingOrder.get().isExpired()) {
            return existingOrder.get().getOrderId();
        }

        ActiveOrder newOrder = orderFactory.createOrder(
                buyer, 
                eventId, 
                Instant.now().plus(TIMEOUT_MINUTES, ChronoUnit.MINUTES)
        );
        orderRepository.save(newOrder);
        return newOrder.getOrderId();
    }

    /**
     * Add a seat to the active order. This involves:
     * 1. Locking the seat through the ZoneService (which will throw an exception if the seat is already taken).
     * 2. If the lock is successful, adding the item to our order aggregate and saving it.
     */
    public void reserveSeat(String orderId, String zoneId, String seatId) {
        ActiveOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Active order not found"));

        // step 1: try to lock the seat through the ZoneService
        OrderItem lockedItem = zoneService.lockSeat(zoneId, seatId, orderId);

        // step 2: if the lock is successful, add the item to our order aggregate and save it
        order.addItem(lockedItem);
        orderRepository.save(order);
    }

    /**
     * Release a seat from the active order. This involves:
     * 1. Removing the item from our order aggregate and saving it.
     * 2. Unlocking the seat through the ZoneService to make it available again.
     */
    public void releaseSeat(String orderId, String zoneId, String seatId) {
        ActiveOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Active order not found"));

        // release the seat from the order aggregate and save it
        order.removeItem(zoneId, seatId);
        orderRepository.save(order);

        // unlock the seat through the ZoneService to make it available again
        zoneService.unlockSeat(zoneId, seatId);
    }

    /**
     * This method should be called periodically (e.g., every minute) 
     * to find and expire any active orders that have passed their expiration time.
     */
    public void sweepExpiredOrders() {
        List<ActiveOrder> expiredOrders = orderRepository.findExpired()
                .orElseThrow(() -> new IllegalStateException("Error fetching expired orders"));
                
        for (ActiveOrder order : expiredOrders) {
            List<OrderItem> expiredItems = order.expire();
            orderRepository.save(order);
            for (OrderItem item : expiredItems) {
                zoneService.unlockSeat(item.getZoneId(), item.getSeatId());
            }
        }
    }
}