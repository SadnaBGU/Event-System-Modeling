package com.eventsystem.infrastructure.recovery;

import com.eventsystem.application.order.OrderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background recovery job (V3 task 1.5).
 *
 * <p>Periodically asks the application layer to release inventory held by abandoned
 * reservations whose timer expired. Because the first run fires shortly after start-up,
 * this also recovers orders that expired while the server was down or busy — their seats
 * are returned to the owning Zone instead of staying locked forever.
 *
 * <p>Disabled in tests via {@code eventsystem.recovery.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "eventsystem.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class RecoverySweeper {

    private static final Logger logger = LoggerFactory.getLogger(RecoverySweeper.class);

    private final OrderService orderService;

    public RecoverySweeper(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(
            initialDelayString = "${eventsystem.recovery.initial-delay-ms:15000}",
            fixedDelayString = "${eventsystem.recovery.sweep-interval-ms:60000}")
    public void sweep() {
        try {
            orderService.sweepExpiredOrders();
        } catch (RuntimeException e) {
            // Never let a sweep failure kill the scheduler thread; just log and retry next tick.
            logger.error("Recovery sweep failed; will retry on next scheduled run", e);
        }
    }
}
