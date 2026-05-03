package com.eventsystem.application.order;

import com.eventsystem.domain.queue.VirtualQueue;
import com.eventsystem.domain.order.BuyerReference;

import java.util.List;
import java.util.UUID;

public class QueueService {
    private final VirtualQueueRepository queueRepository;
    private final NotificationPort notificationPort;
    
    private static final int QUEUE_LOAD_THRESHOLD = 100;
    private static final int MAX_CONCURRENT_ADMISSIONS = 100;
    private static final int TOKEN_VALIDITY_MINUTES = 10;

    public QueueService(VirtualQueueRepository queueRepository, NotificationPort notificationPort) {
        this.queueRepository = queueRepository;
        this.notificationPort = notificationPort;
    }

    /**
     * Enqueue a visitor when they attempt to access the event page. 
     * this method will be called from the web layer when a user tries to access the event.
     * it will either create a new queue for the event or add the user to the existing queue. 
     */
    public void enqueueVisitor(String eventId, BuyerReference buyer) {
        VirtualQueue queue = queueRepository.findByEvent(eventId)
                .orElseGet(() -> createNewQueue(eventId));

        queue.enqueue(buyer);
        queueRepository.save(queue);
    }

    /**
     * Process the next batch of visitors in the queue. 
     * this method will be called by a scheduled task that runs every minute.
     */
    public void processNextBatch(String eventId) {
        queueRepository.findByEvent(eventId).ifPresent(queue -> {
            List<BuyerReference> newlyAdmitted = queue.admitNext(TOKEN_VALIDITY_MINUTES);
            queueRepository.save(queue);

            // send notifications to the newly admitted buyers
            for (BuyerReference buyer : newlyAdmitted) {
                notificationPort.sendQueueTurnArrived(buyer, eventId);
            }
        });
    }

    /**
     * Revoke admission for a buyer. 
     * this method can be called if, for example, the buyer fails
     * to complete the purchase within the token validity period, or if they violate some rules.
     */
    public void revokeAdmission(String eventId, BuyerReference buyer) {
        queueRepository.findByEvent(eventId).ifPresent(queue -> {
            queue.revokeAdmission(buyer);
            queueRepository.save(queue);
        });
    }

    /**
     * Check the admission status of a buyer for a specific event.
     */
    public boolean checkAdmissionStatus(String eventId, BuyerReference buyer) {
        return queueRepository.findByEvent(eventId)
                .map(queue -> queue.isAdmitted(buyer))
                .orElse(false);
    }
    
    private VirtualQueue createNewQueue(String eventId) {
        VirtualQueue newQueue = new VirtualQueue(
                UUID.randomUUID().toString(), 
                eventId, 
                QUEUE_LOAD_THRESHOLD, 
                MAX_CONCURRENT_ADMISSIONS
        );
        newQueue.activate();
        return newQueue;
    }
}
