package com.eventsystem.domain.queue;

import com.eventsystem.domain.domainexceptions.QueueIsNotActiveException;
import com.eventsystem.domain.order.BuyerReference;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "virtual_queues")
public class VirtualQueue implements Persistable<String> {

    @Id
    @Column(name = "queue_id")
    private String queueId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private QueueStatus status;

    @Column(name = "load_threshold")
    private int loadThreshold;

    @Column(name = "max_concurrent_admissions")
    private int maxConcurrentAdmissions;

    @Version
    @Column(name = "version")
    private long version;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "queue_entries", joinColumns = @JoinColumn(name = "queue_id"))
    @OrderBy("position ASC")
    private List<QueueEntry> waitingEntries;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "queue_admissions", joinColumns = @JoinColumn(name = "queue_id"))
    private List<AdmissionToken> admittedSet;
    
    @Column(name = "next_position_counter")
    private int nextPositionCounter = 1;

    protected VirtualQueue() {}

    public VirtualQueue(String queueId, String eventId, int loadThreshold, int maxConcurrentAdmissions) {
        this.queueId = Objects.requireNonNull(queueId, "queueId must not be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.status = QueueStatus.INACTIVE;
        this.loadThreshold = loadThreshold;
        this.maxConcurrentAdmissions = maxConcurrentAdmissions;
        this.version = 0L;
        this.waitingEntries = new ArrayList<>();
        this.admittedSet = new ArrayList<>();
    }

    public VirtualQueue(
         String queueId,
         String eventId,
         QueueStatus status,
         int loadThreshold,
         int maxConcurrentAdmissions,
         List<QueueEntry> waitingEntries,
         List<AdmissionToken> admittedSet,
         int nextPositionCounter) {
        
        this.queueId = queueId;
        this.eventId = eventId;
        this.status = status;
        this.loadThreshold = loadThreshold;
        this.maxConcurrentAdmissions = maxConcurrentAdmissions;
        this.waitingEntries = new ArrayList<>(waitingEntries);
        this.admittedSet = new ArrayList<>(admittedSet);
        this.nextPositionCounter = nextPositionCounter;
        this.version = 0L;
    }

    public synchronized void activate() {
        this.status = QueueStatus.ACTIVE;
    }

    public synchronized void pause() {
        this.status = QueueStatus.INACTIVE;
    }

    public synchronized int joinQueue(BuyerReference visitor) {
        if (status != QueueStatus.ACTIVE) {
            throw new QueueIsNotActiveException("Queue is not active");
        }
        
        if (isAdmitted(visitor)) {
            return 0;
        }

        int existingPos = positionOf(visitor);
        if (existingPos > 0) {
            return existingPos;
        }

        int assignedPos = nextPositionCounter++;
        waitingEntries.add(new QueueEntry(visitor, assignedPos));
        return assignedPos;
    }

    public synchronized List<AdmissionToken> admitNextGroup(int tokenValidityMinutes) {
        if (status != QueueStatus.ACTIVE) {
            return List.of();
        }

        expireTokens();

        int currentAdmittedCount = admittedSet.size();
        int availableSlots = maxConcurrentAdmissions - currentAdmittedCount;
        
        List<AdmissionToken> newlyAdmitted = new ArrayList<>();
        
        while (availableSlots > 0 && !waitingEntries.isEmpty()) {
            QueueEntry nextInLine = waitingEntries.remove(0);
            AdmissionToken token = new AdmissionToken(nextInLine.getVisitorRef(), tokenValidityMinutes);
            admittedSet.add(token);
            newlyAdmitted.add(token);
            availableSlots--;
        }
        
        return newlyAdmitted;
    }

    public synchronized void revokeAdmission(BuyerReference buyer) {
        admittedSet.removeIf(token -> token.getBuyerRef().equals(buyer));
    }

    public synchronized boolean isAdmitted(BuyerReference visitor) {
        expireTokens();
        return admittedSet.stream()
                .anyMatch(t -> t.getBuyerRef().equals(visitor) && !t.isExpired());
    }

    public synchronized void expireTokens() {
        admittedSet.removeIf(AdmissionToken::isExpired);
    }

    public synchronized int getOpenSlots() {
        expireTokens();
        int currentLoad = (int) admittedSet.stream()
                .filter(token -> !token.isConsumed())
                .count();
        return Math.max(0, maxConcurrentAdmissions - currentLoad);
    }

    public synchronized int positionOf(BuyerReference visitor) {
        if (isAdmitted(visitor)) return 0;
        
        for (QueueEntry e : waitingEntries) {
            if (e.getVisitorRef().equals(visitor)) return e.getPosition();
        }
        return -1;
    }
    
    public synchronized void consumeTokenFor(BuyerReference buyer) {
        admittedSet.stream()
                .filter(t -> t.getBuyerRef().equals(buyer) && !t.isExpired())
                .findFirst()
                .ifPresent(AdmissionToken::markConsumed);
    }

    public synchronized List<BuyerReference> clearQueue() {
        this.status = QueueStatus.INACTIVE;
        List<BuyerReference> waiting = waitingEntries.stream()
                .map(QueueEntry::getVisitorRef)
                .toList();
        waitingEntries.clear();
        return waiting;
    }

    public String getQueueId() { return queueId; }
    public String getEventId() { return eventId; }
    public QueueStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public int getLoadThreshold() { return loadThreshold; }
    public int getMaxConcurrentAdmissions() { return maxConcurrentAdmissions; }

    @Transient
    @Override
    public boolean isNew() {
        return this.version == 0L;
    }

    @Transient
    @Override
    public String getId() {
        return this.queueId;
    }
}