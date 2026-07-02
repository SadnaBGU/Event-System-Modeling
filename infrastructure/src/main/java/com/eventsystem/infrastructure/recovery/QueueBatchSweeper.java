package com.eventsystem.infrastructure.recovery;

import com.eventsystem.application.order.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "eventsystem.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class QueueBatchSweeper {

    private static final Logger logger = LoggerFactory.getLogger(QueueBatchSweeper.class);

    private final QueueService queueService;

    public QueueBatchSweeper(QueueService queueService) {
        this.queueService = queueService;
    }

    @Scheduled(
            initialDelayString = "${eventsystem.queue.initial-delay-ms:15000}",
            fixedDelayString = "${eventsystem.queue.batch-interval-ms:5000}")
    public void processQueueBatches() {
        try {
            queueService.processNextBatchForAllEvents();
        } catch (RuntimeException e) {
            logger.error("Queue batch sweep failed; will retry on next scheduled run", e);
        }
    }
}