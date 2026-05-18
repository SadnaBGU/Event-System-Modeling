package com.eventsystem.domain.queue;

import com.eventsystem.domain.domainexceptions.QueueIsNotActiveException;
import com.eventsystem.domain.order.BuyerReference;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class VirtualQueue {
    private final String queueId;
    private final String eventId;
    private QueueStatus status;
    private final int loadThreshold;
    private final int maxConcurrentAdmissions;
    private long version;

    private final LinkedList<QueueEntry> waitingEntries;
    private final List<AdmissionToken> admittedSet;
    
    private int nextPositionCounter = 1;

    public VirtualQueue(String queueId, String eventId, int loadThreshold, int maxConcurrentAdmissions) {
        this.queueId = queueId;
        this.eventId = eventId;
        this.status = QueueStatus.INACTIVE;
        this.loadThreshold = loadThreshold;
        this.maxConcurrentAdmissions = maxConcurrentAdmissions;
        this.version = 0L;
        this.waitingEntries = new LinkedList<>();
        this.admittedSet = new ArrayList<>();
    }

    public VirtualQueue(
        String queueId,
         String eventId,
         QueueStatus status,
         int loadThreshold,
         int maxConcurrentAdmissions,
         long version,
         LinkedList<QueueEntry> waitingEntries,
         List<AdmissionToken> admittedSet
        ) {
        this.queueId = queueId;
        this.eventId = eventId;
        this.status = status;
        this.loadThreshold = loadThreshold;
        this.maxConcurrentAdmissions = maxConcurrentAdmissions;
        this.version = version;
        this.waitingEntries = waitingEntries;
        this.admittedSet = admittedSet;
    }

    public void activate() {
        this.status = QueueStatus.ACTIVE;
        this.version++;
    }

    public void enqueue(BuyerReference visitor) {
        if (status != QueueStatus.ACTIVE) {
            throw new QueueIsNotActiveException(eventId);
        }
        
        boolean alreadyWaiting = waitingEntries.stream()
                .anyMatch(e -> e.getVisitorRef().equals(visitor));
        
        if (!alreadyWaiting && !isAdmitted(visitor)) {
            waitingEntries.add(new QueueEntry(visitor, nextPositionCounter++));
            this.version++;
        }
    }

    public List<BuyerReference> admitNext(int tokenValidityMinutes) {
        expireTokens(); 
        
        int openSlots = getOpenSlots();
        List<BuyerReference> newlyAdmitted = new ArrayList<>();

        while (openSlots > 0 && !waitingEntries.isEmpty()) {
            QueueEntry nextInLine = waitingEntries.poll();
            AdmissionToken token = new AdmissionToken(nextInLine.getVisitorRef(), tokenValidityMinutes);
            admittedSet.add(token);
            newlyAdmitted.add(nextInLine.getVisitorRef());
            openSlots--;
        }
        if (!newlyAdmitted.isEmpty()) {
            this.version++;
        }
        return newlyAdmitted;
    }

    public void revokeAdmission(BuyerReference buyer) {
        admittedSet.removeIf(token -> token.getBuyerRef().equals(buyer));
        this.version++;
    }

    public boolean isAdmitted(BuyerReference visitor) {
        expireTokens();
        return admittedSet.stream()
                .anyMatch(token -> token.getBuyerRef().equals(visitor) && !token.isConsumed());
    }

    public void expireTokens() {
        admittedSet.removeIf(AdmissionToken::isExpired);
    }

    public int getOpenSlots() {
        int currentLoad = (int) admittedSet.stream()
                .filter(token -> !token.isExpired() && !token.isConsumed())
                .count();
        return Math.max(0, maxConcurrentAdmissions - currentLoad);
    }
    
    public void consumeTokenFor(BuyerReference buyer) {
        admittedSet.stream()
                .filter(t -> t.getBuyerRef().equals(buyer) && !t.isExpired())
                .findFirst()
                .ifPresent(AdmissionToken::markConsumed);
    }

    public List<BuyerReference> clearQueue() {
        this.status = QueueStatus.INACTIVE;
        List<BuyerReference> waiting = waitingEntries.stream()
                .map(QueueEntry::getVisitorRef)
                .toList();
        waitingEntries.clear();
        this.version++;
        return waiting;
    }

    public String getQueueId() { 
        return queueId; 
    }

    public String getEventId() { 
        return eventId; 
    }

    public QueueStatus getStatus() { 
        return status; 
    }

    public long getVersion() { 
        return version; 
    }

    public int getLoadThreshold() { 
        return loadThreshold; 
    }

    public int getMaxConcurrentAdmissions() { 
        return maxConcurrentAdmissions; 
    }
}