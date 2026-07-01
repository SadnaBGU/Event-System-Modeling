package com.eventsystem.application.order;

import com.eventsystem.application.appexceptions.QueueAdmissionRequiredException;
import com.eventsystem.domain.queue.IVirtualQueueRepository;
import com.eventsystem.domain.queue.VirtualQueue;
import com.eventsystem.domain.queue.AdmissionToken;
import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.IActiveOrderRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class QueueService {
    private final Logger logger = LoggerFactory.getLogger(QueueService.class);
    
    private final IVirtualQueueRepository queueRepository;
    private final IActiveOrderRepository activeOrderRepository;
    private final INotificationPort notificationPort;
    private static final int QUEUE_LOAD_THRESHOLD = 3;
    private static final int MAX_CONCURRENT_ADMISSIONS = 1;
    private static final int TOKEN_VALIDITY_MINUTES = 10;

    public QueueService(IVirtualQueueRepository queueRepository,
                        IActiveOrderRepository activeOrderRepository,
                        INotificationPort notificationPort) {
        this.queueRepository = queueRepository;
        this.activeOrderRepository = activeOrderRepository;
        this.notificationPort = notificationPort;
    }

    /**
     * Enforces queue admission on high concurrent load.
     * If queue flow is not required, returns immediately.
     */
    @Transactional
    public void requireAdmissionOrEnqueueOnHighLoad(String eventId, BuyerReference buyer) {
        if (!isHighLoad(eventId)) {
            return;
        }

        VirtualQueue queue = getOrCreateActiveQueue(eventId);
        queue.joinQueue(buyer);
        queueRepository.save(queue);

        if (!queue.isAdmitted(buyer)) {
            throw new QueueAdmissionRequiredException(eventId);
        }

        // Buyer is entering checkout now, so this admission slot can be consumed and
        // immediately reused by the next waiting buyer.
        queue.consumeTokenFor(buyer);
        List<AdmissionToken> newlyAdmitted = queue.admitNextGroup(TOKEN_VALIDITY_MINUTES);
        queueRepository.save(queue);

        for (AdmissionToken token : newlyAdmitted) {
            notificationPort.sendQueueTurnArrived(token.getBuyerRef(), eventId);
        }
    }

    public void enqueueVisitor(String eventId, BuyerReference buyer) {
        logger.info("Attempting to enqueue visitor for event {}", eventId);

        VirtualQueue queue = getOrCreateActiveQueue(eventId);

        queue.joinQueue(buyer);
        queueRepository.save(queue);

        logger.info("Successfully enqueued visitor for event {}", eventId);
    }

    public void processNextBatch(String eventId) {
        logger.info("Processing next batch of visitors for event {}", eventId);

        queueRepository.findByEvent(eventId).ifPresentOrElse(queue -> {
            List<AdmissionToken> newlyAdmitted = queue.admitNextGroup(TOKEN_VALIDITY_MINUTES);
            queueRepository.save(queue);

            for (AdmissionToken token : newlyAdmitted) {
                notificationPort.sendQueueTurnArrived(token.getBuyerRef(), eventId);
            }
            logger.info("Successfully admitted {} new visitors for event {}", newlyAdmitted.size(), eventId);
        }, () -> {
            logger.warn("Failed to process batch: No active queue found for event {}", eventId);        
        });
    }

    public void revokeAdmission(String eventId, BuyerReference buyer) {
        logger.info("Attempting to revoke admission for buyer in event {}", eventId);
        
        queueRepository.findByEvent(eventId).ifPresentOrElse(queue -> {
            queue.revokeAdmission(buyer);
            queueRepository.save(queue);
            
            logger.info("Successfully revoked admission for buyer in event {}", eventId);
            
        }, () -> {
            logger.warn("Failed to revoke admission: No active queue found for event {}", eventId);
        });
    }

    public boolean checkAdmissionStatus(String eventId, BuyerReference buyer) {
        logger.info("Checking admission status for buyer in event {}", eventId);
        
        boolean status = queueRepository.findByEvent(eventId)
                .map(queue -> queue.isAdmitted(buyer))
                .orElse(false);
                
        logger.info("Admission status for buyer in event {}: {}", eventId, status);
        
        return status;
    }

    public void processNextBatchForAllEvents() {
        queueRepository.findAll().forEach(queue -> processNextBatch(queue.getEventId()));
    }

    @Transactional(readOnly = true)
    public AdmissionStatus getAdmissionStatus(String eventId, BuyerReference buyer) {
        return queueRepository.findByEvent(eventId)
                .map(queue -> new AdmissionStatus(queue.isAdmitted(buyer), queue.positionOf(buyer)))
                .orElse(new AdmissionStatus(false, -1));
    }

    public static class AdmissionStatus {
        public final boolean isAdmitted;
        public final int position;

        public AdmissionStatus(boolean isAdmitted, int position) {
            this.isAdmitted = isAdmitted;
            this.position = position;
        }
    }
    
    private VirtualQueue createNewQueue(String eventId) {
        VirtualQueue newQueue = new VirtualQueue(
                UUID.randomUUID().toString(), 
                eventId, 
                QUEUE_LOAD_THRESHOLD, 
                MAX_CONCURRENT_ADMISSIONS
        );
        newQueue.activate();

        logger.info("Created and activated new VirtualQueue {} for event {}", newQueue.getQueueId(), eventId);

        return newQueue;
    }

    private VirtualQueue getOrCreateActiveQueue(String eventId) {
        return queueRepository.findByEvent(eventId)
                .map(queue -> {
                    if (!queue.getStatus().equals(com.eventsystem.domain.queue.QueueStatus.ACTIVE)) {
                        queue.activate();
                        logger.info("Re-activated existing VirtualQueue {} for event {}", queue.getQueueId(), eventId);
                    }
                    return queue;
                })
                .orElseGet(() -> {
                    logger.info("No existing queue found for event {}. Creating a new one.", eventId);
                    return createNewQueue(eventId);
                });
    }

    public void handleEventSoldOut(String eventId) {
        queueRepository.findByEvent(eventId).ifPresent(queue -> {
            List<BuyerReference> disappointedBuyers = queue.clearQueue();
            queueRepository.save(queue);
            
            for (BuyerReference buyer : disappointedBuyers) {
                notificationPort.sendEventSoldOut(buyer, eventId);
            }
        });
    }

    @Transactional
    public void resetAllQueues() {
        logger.info("Resetting all queues in the system due to startup or recovery.");
        queueRepository.deleteAll();
        logger.info("All queues have been reset.");
    }

    private boolean isHighLoad(String eventId) {
        long activeCount = activeOrderRepository.countActiveNonExpiredByEvent(eventId, Instant.now());
        return activeCount >= QUEUE_LOAD_THRESHOLD;
    }
}