package com.eventsystem.domain.queue;

import com.eventsystem.domain.domainexceptions.QueueIsNotActiveException;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class VirtualQueueTest {

    private VirtualQueue queue;
    private final String queueId = "QUEUE-2026";
    private final String eventId = "EVENT-2026";
    private final int maxAdmissions = 100;

    @BeforeEach
    void setUp() {
        queue = new VirtualQueue(queueId, eventId, 50, maxAdmissions);
    }

    // ==========================================
    // Constructors & Persistable 
    // ==========================================

    @Test
    void jpaConstructor_createsEmptyInstance() throws Exception {
        java.lang.reflect.Constructor<VirtualQueue> c = VirtualQueue.class.getDeclaredConstructor();
        c.setAccessible(true);
        VirtualQueue vq = c.newInstance();
        
        assertThat(vq.getId()).isNull();
        assertThat(vq.isNew()).isTrue();
    }

    @Test
    void fullConstructor_andGetters_workCorrectly() {
        VirtualQueue vq = new VirtualQueue("Q-1", "E-1", QueueStatus.ACTIVE, 50, 100, List.of(), List.of(), 5);
        
        assertThat(vq.getQueueId()).isEqualTo("Q-1");
        assertThat(vq.getEventId()).isEqualTo("E-1");
        assertThat(vq.getStatus()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(vq.getLoadThreshold()).isEqualTo(50);
        assertThat(vq.getMaxConcurrentAdmissions()).isEqualTo(100);
        assertThat(vq.getVersion()).isEqualTo(0L);
        assertThat(vq.isNew()).isTrue();
        assertThat(vq.getId()).isEqualTo("Q-1");
    }

    // ==========================================
    // Queue Status Lifecycle
    // ==========================================

    @Test
    void pause_setsStatusToInactive() {
        queue.activate();
        assertThat(queue.getStatus()).isEqualTo(QueueStatus.ACTIVE);
        
        queue.pause();
        assertThat(queue.getStatus()).isEqualTo(QueueStatus.INACTIVE);
    }

    @Test
    void clearQueue_setsInactive_and_returnsWaitingEntries() {
        queue.activate();
        BuyerReference v1 = new BuyerReference(BuyerType.MEMBER, "s1", "m1");
        queue.joinQueue(v1);
        
        List<BuyerReference> waiting = queue.clearQueue();
        
        assertThat(queue.getStatus()).isEqualTo(QueueStatus.INACTIVE);
        assertThat(waiting).containsExactly(v1);
        assertThat(queue.positionOf(v1)).isEqualTo(-1); // Queue is cleared
    }

    // ==========================================
    // Core Queue Operations
    // ==========================================

    @Test
    void enqueue_InactiveQueue_ThrowsException() {
        BuyerReference visitor = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");

        assertThatThrownBy(() -> queue.joinQueue(visitor))
                .isInstanceOf(QueueIsNotActiveException.class);
    }

    @Test
    void enqueue_ActiveQueueSingleVisitor_AddsToWaitingEntries() {
        queue.activate();
        BuyerReference visitor = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");

        queue.joinQueue(visitor);

        assertThat(queue.isAdmitted(visitor)).isFalse();
    }

    @Test
    void enqueue_DuplicateVisitor_IgnoresDuplicate() {
        queue.activate();
        BuyerReference visitor = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");
        
        queue.joinQueue(visitor);
        int initialOpenSlots = queue.getOpenSlots();

        queue.joinQueue(visitor);

        assertThat(queue.getOpenSlots()).isEqualTo(initialOpenSlots);
    }

    @Test
    void enqueue_MultipleUniqueVisitors_AllAdded() {
        queue.activate();
        List<BuyerReference> visitors = createBuyerReferences(10);

        for (BuyerReference visitor : visitors) {
            queue.joinQueue(visitor);
        }

        for (BuyerReference visitor : visitors) {
            assertThat(queue.isAdmitted(visitor)).isFalse();
        }
    }

    @Test
    void admitNextGroup_whenNotActive_returnsEmpty() {
        // queue is inactive initially
        List<AdmissionToken> tokens = queue.admitNextGroup(10);
        assertThat(tokens).isEmpty();
    }

    @Test
    void admitNext_SlidingWindowLimit_OnlyAdmitsUpToMaxConcurrent() {
        queue = new VirtualQueue(queueId, eventId, 50, 100);
        queue.activate();
        
        List<BuyerReference> visitors = createBuyerReferences(150);
        for (BuyerReference visitor : visitors) {
            queue.joinQueue(visitor);
        }

        List<AdmissionToken> newlyAdmitted = queue.admitNextGroup(10);

        assertThat(newlyAdmitted).hasSize(100);
        List<BuyerReference> newlyAdmittedBuyers = newlyAdmitted.stream().map(AdmissionToken::getBuyerRef).toList();
        assertThat(newlyAdmittedBuyers).containsExactlyElementsOf(visitors.subList(0, 100));
        
        for (int i = 0; i < 100; i++) {
            assertThat(queue.isAdmitted(visitors.get(i))).isTrue();
        }
        
        for (int i = 100; i < 150; i++) {
            assertThat(queue.isAdmitted(visitors.get(i))).isFalse();
        }
    }

    @Test
    void admitNext_PartialAdmission_LessVisitorsThanSlots() {
        queue.activate();
        List<BuyerReference> visitors = createBuyerReferences(50);
        for (BuyerReference visitor : visitors) {
            queue.joinQueue(visitor);
        }

        List<AdmissionToken> newlyAdmitted = queue.admitNextGroup(10);

        assertThat(newlyAdmitted).hasSize(50);
        List<BuyerReference> newlyAdmittedBuyers = newlyAdmitted.stream().map(AdmissionToken::getBuyerRef).toList();
        assertThat(newlyAdmittedBuyers).containsExactlyElementsOf(visitors);
        
        for (BuyerReference visitor : visitors) {
            assertThat(queue.isAdmitted(visitor)).isTrue();
        }
    }

    @Test
    void admitNext_FullyAdmittedQueue_DoesNotAdmitMore() {
        queue = new VirtualQueue(queueId, eventId, 50, 50);
        queue.activate();
        
        List<BuyerReference> first50 = createBuyerReferences(50);
        List<BuyerReference> next50 = createBuyerReferences(50, 50);
        
        for (BuyerReference visitor : first50) {
            queue.joinQueue(visitor);
        }
        for (BuyerReference visitor : next50) {
            queue.joinQueue(visitor);
        }

        List<AdmissionToken> firstBatch = queue.admitNextGroup(10);
        List<AdmissionToken> secondBatch = queue.admitNextGroup(10);

        assertThat(firstBatch).hasSize(50);
        assertThat(secondBatch).isEmpty();
        
        for (BuyerReference visitor : next50) {
            assertThat(queue.isAdmitted(visitor)).isFalse();
        }
    }

    // ==========================================
    // Queries & Token Operations
    // ==========================================

    @Test
    void positionOf_returnsCorrectValues() {
        queue = new VirtualQueue(queueId, eventId, 50, 1);
        queue.activate();
        
        BuyerReference v1 = new BuyerReference(BuyerType.MEMBER, "s1", "m1");
        BuyerReference v2 = new BuyerReference(BuyerType.MEMBER, "s2", "m2");
        BuyerReference v3 = new BuyerReference(BuyerType.MEMBER, "s3", "m3");

        queue.joinQueue(v1);
        queue.joinQueue(v2);

        // Admits v1 because maxConcurrent is 1
        queue.admitNextGroup(10); 

        assertThat(queue.positionOf(v1)).isEqualTo(0); // Admitted
        assertThat(queue.positionOf(v2)).isGreaterThan(0); // Waiting
        assertThat(queue.positionOf(v3)).isEqualTo(-1); // Not in queue
    }

    @Test
    void isAdmitted_AdmittedVisitor_ReturnsTrue() {
        queue.activate();
        BuyerReference visitor = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");
        
        queue.joinQueue(visitor);
        queue.admitNextGroup(10);

        boolean admitted = queue.isAdmitted(visitor);

        assertThat(admitted).isTrue();
    }

    @Test
    void isAdmitted_WaitingVisitor_ReturnsFalse() {
        queue = new VirtualQueue(queueId, eventId, 50, 2);
        queue.activate();
        
        BuyerReference visitor1 = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");
        BuyerReference visitor2 = new BuyerReference(BuyerType.MEMBER, "session-2", "member-2");
        BuyerReference visitor3 = new BuyerReference(BuyerType.MEMBER, "session-3", "member-3");
        
        queue.joinQueue(visitor1);
        queue.joinQueue(visitor2);
        queue.joinQueue(visitor3);
        
        queue.admitNextGroup(10);

        boolean admitted = queue.isAdmitted(visitor3);

        assertThat(admitted).isFalse();
    }

    @Test
    void isAdmitted_NeverEnqueued_ReturnsFalse() {
        queue.activate();
        BuyerReference visitor = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");

        boolean admitted = queue.isAdmitted(visitor);

        assertThat(admitted).isFalse();
    }

    @Test
    void isAdmitted_TokenExpired_ReturnsFalseAndPurgesToken() {
        queue = new VirtualQueue(queueId, eventId, 50, 100);
        queue.activate();
        
        BuyerReference visitor = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");
        queue.joinQueue(visitor);
        
        // 0 minutes validity means it expires instantly
        queue.admitNextGroup(0);

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        boolean admitted = queue.isAdmitted(visitor);

        assertThat(admitted).isFalse();
    }

    @Test
    void revokeAdmission_RemovesAdmittedVisitor() {
        queue.activate();
        BuyerReference visitor = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");
        
        queue.joinQueue(visitor);
        queue.admitNextGroup(10);
        
        assertThat(queue.isAdmitted(visitor)).isTrue();
        assertThat(queue.getOpenSlots()).isEqualTo(maxAdmissions - 1);

        queue.revokeAdmission(visitor);

        assertThat(queue.isAdmitted(visitor)).isFalse();
        assertThat(queue.getOpenSlots()).isEqualTo(maxAdmissions);
    }

    @Test
    void consumeTokenFor_marksTokenAsConsumed() {
        queue.activate();
        BuyerReference v1 = new BuyerReference(BuyerType.MEMBER, "s1", "m1");
        queue.joinQueue(v1);
        queue.admitNextGroup(10);

        assertThat(queue.getOpenSlots()).isEqualTo(99);
        
        queue.consumeTokenFor(v1);
        
        // After consuming, open slots should go back to 100 because consumed tokens don't count towards current load
        assertThat(queue.getOpenSlots()).isEqualTo(100);
    }

    @Test
    void expireTokens_RemovesExpiredTokensAndFreesSlots() {
        queue = new VirtualQueue(queueId, eventId, 50, 5);
        queue.activate();
        
        List<BuyerReference> first5 = createBuyerReferences(5);
        List<BuyerReference> next5 = createBuyerReferences(5, 5);
        
        for (BuyerReference visitor : first5) {
            queue.joinQueue(visitor);
        }
        for (BuyerReference visitor : next5) {
            queue.joinQueue(visitor);
        }
        
        queue.admitNextGroup(0); // instant expire
        
        for (BuyerReference visitor : next5) {
            assertThat(queue.isAdmitted(visitor)).isFalse();
        }
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        List<AdmissionToken> nowAdmitted = queue.admitNextGroup(10);

        assertThat(nowAdmitted).hasSize(5);
        List<BuyerReference> newlyAdmittedBuyers = nowAdmitted.stream().map(AdmissionToken::getBuyerRef).toList();
        assertThat(newlyAdmittedBuyers).containsExactlyElementsOf(next5);
        
        for (BuyerReference visitor : next5) {
            assertThat(queue.isAdmitted(visitor)).isTrue();
        }
    }

    @Test
    void getOpenSlots_ReflectsCurrentLoad() {
        queue = new VirtualQueue(queueId, eventId, 50, 100);
        queue.activate();
        
        List<BuyerReference> visitors = createBuyerReferences(30);
        for (BuyerReference visitor : visitors) {
            queue.joinQueue(visitor);
        }

        assertThat(queue.getOpenSlots()).isEqualTo(100);
        
        queue.admitNextGroup(10);
        assertThat(queue.getOpenSlots()).isEqualTo(70);
        
        queue.revokeAdmission(visitors.get(0));
        assertThat(queue.getOpenSlots()).isEqualTo(71);
    }

    // ==========================================
    // Helpers
    // ==========================================

    private List<BuyerReference> createBuyerReferences(int count) {
        return createBuyerReferences(count, 0);
    }

    private List<BuyerReference> createBuyerReferences(int count, int startId) {
        List<BuyerReference> buyers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int id = startId + i;
            buyers.add(new BuyerReference(
                    BuyerType.MEMBER,
                    "session-" + id,
                    "member-" + id
            ));
        }
        return buyers;
    }
}