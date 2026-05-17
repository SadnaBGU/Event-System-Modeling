package com.eventsystem.application.order;

import com.eventsystem.domain.queue.VirtualQueue;
import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.domain.order.BuyerReference;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueService {
    private final Logger logger = LoggerFactory.getLogger(QueueService.class);
    
    private final IVirtualQueueRepository queueRepository;
    private final INotificationPort notificationPort;
    private static final int QUEUE_LOAD_THRESHOLD = 100;
    private static final int MAX_CONCURRENT_ADMISSIONS = 100;
    private static final int TOKEN_VALIDITY_MINUTES = 10;

    public QueueService(IVirtualQueueRepository queueRepository, INotificationPort notificationPort) {
        this.queueRepository = queueRepository;
        this.notificationPort = notificationPort;
    }

    /**
     * Enqueue a visitor when they attempt to access the event page. 
     * this method will be called from the web layer when a user tries to access the event.
     * it will either create a new queue for the event or add the user to the existing queue. 
     */
    public void enqueueVisitor(String eventId, BuyerReference buyer) {
        logger.info("Attempting to enqueue visitor for event {}", eventId);

        VirtualQueue queue = queueRepository.findByEvent(eventId)
                .orElseGet(() -> {
                    logger.info("No existing queue found for event {}. Creating a new one.", eventId);
                    return createNewQueue(eventId);
                });

        queue.enqueue(buyer);
        queueRepository.save(queue);

        logger.info("Successfully enqueued visitor for event {}", eventId);
    }

    /**
     * Process the next batch of visitors in the queue. 
     * this method will be called by a scheduled task that runs every minute.
     */
    public void processNextBatch(String eventId) {
        logger.info("Processing next batch of visitors for event {}", eventId);

        queueRepository.findByEvent(eventId).ifPresentOrElse(queue -> {
            List<BuyerReference> newlyAdmitted = queue.admitNext(TOKEN_VALIDITY_MINUTES);
            queueRepository.save(queue);

            // send notifications to the newly admitted buyers
            for (BuyerReference buyer : newlyAdmitted) {
                notificationPort.sendQueueTurnArrived(buyer, eventId);
            }
            logger.info("Successfully admitted {} new visitors for event {}", newlyAdmitted.size(), eventId);
        }, () -> {
            logger.warn("Failed to process batch: No active queue found for event {}", eventId);        
        });
    }

    /**
     * Revoke admission for a buyer. 
     * this method can be called if, for example, the buyer fails
     * to complete the purchase within the token validity period, or if they violate some rules.
     */
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

    /**
     * Check the admission status of a buyer for a specific event.
     */
    public boolean checkAdmissionStatus(String eventId, BuyerReference buyer) {
        logger.info("Checking admission status for buyer in event {}", eventId);
        
        boolean status = queueRepository.findByEvent(eventId)
                .map(queue -> queue.isAdmitted(buyer))
                .orElse(false);
                
        logger.info("Admission status for buyer in event {}: {}", eventId, status);
        
        return status;
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

    public void handleEventSoldOut(String eventId) {
        queueRepository.findByEvent(eventId).ifPresent(queue -> {
            List<BuyerReference> disappointedBuyers = queue.clearQueue();
            queueRepository.save(queue);
            
            for (BuyerReference buyer : disappointedBuyers) {
                notificationPort.sendEventSoldOut(buyer, eventId);
            }
        });
    }
}
