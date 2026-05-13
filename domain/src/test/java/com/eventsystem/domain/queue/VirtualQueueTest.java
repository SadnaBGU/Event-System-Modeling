package com.eventsystem.domain.queue;

import com.eventsystem.domain.domainexceptions.QueueIsNotActiveException;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Track A: Domain Unit Tests for VirtualQueue aggregate.
 * Pure logic testing - NO Mockito, NO external dependencies.
 * Tests sliding window behavior, token expiry, and queue admission logic.
 */
class VirtualQueueTest {

    private VirtualQueue queue;
    private final String queueId = "QUEUE-2026";
    private final String eventId = "EVENT-2026";
    private final int maxAdmissions = 100;

    @BeforeEach
    void setUp() {
        queue = new VirtualQueue(queueId, eventId, 50, maxAdmissions);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Test 1: enqueue() - Adding visitors to queue
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void enqueue_InactiveQueue_ThrowsException() {
        // Arrange
        BuyerReference visitor = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");

        // Act & Assert - Queue is not active by default
        assertThatThrownBy(() -> queue.enqueue(visitor))
                .isInstanceOf(QueueIsNotActiveException.class);
    }

    @Test
    void enqueue_ActiveQueueSingleVisitor_AddsToWaitingEntries() {
        // Arrange
        queue.activate();
        BuyerReference visitor = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");

        // Act
        queue.enqueue(visitor);

        // Assert - Visitor should not be immediately admitted (queue is empty)
        assertThat(queue.isAdmitted(visitor)).isFalse();
    }

    @Test
    void enqueue_DuplicateVisitor_IgnoresDuplicate() {
        // Arrange
        queue.activate();
        BuyerReference visitor = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");
        
        queue.enqueue(visitor);
        int initialOpenSlots = queue.getOpenSlots();

        // Act - Try to enqueue same visitor again
        queue.enqueue(visitor);

        // Assert - Open slots unchanged, duplicate ignored
        assertThat(queue.getOpenSlots()).isEqualTo(initialOpenSlots);
    }

    @Test
    void enqueue_MultipleUniqueVisitors_AllAdded() {
        // Arrange
        queue.activate();
        List<BuyerReference> visitors = createBuyerReferences(10);

        // Act
        for (BuyerReference visitor : visitors) {
            queue.enqueue(visitor);
        }

        // Assert - All visitors not yet admitted
        for (BuyerReference visitor : visitors) {
            assertThat(queue.isAdmitted(visitor)).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Test 2: admitNext() - Sliding window admission
    // CRITICAL: 150 enqueue → 100 admitted, 50 waiting
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void admitNext_SlidingWindowLimit_OnlyAdmitsUpToMaxConcurrent() {
        // Arrange - Create queue with max 100 concurrent admissions
        queue = new VirtualQueue(queueId, eventId, 50, 100);
        queue.activate();
        
        List<BuyerReference> visitors = createBuyerReferences(150);
        for (BuyerReference visitor : visitors) {
            queue.enqueue(visitor);
        }

        // Act
        List<BuyerReference> newlyAdmitted = queue.admitNext(10);

        // Assert
        assertThat(newlyAdmitted)
                .hasSize(100)
                .containsExactlyElementsOf(visitors.subList(0, 100));
        
        // Verify that first 100 are admitted
        for (int i = 0; i < 100; i++) {
            assertThat(queue.isAdmitted(visitors.get(i))).isTrue();
        }
        
        // Verify that last 50 are still waiting
        for (int i = 100; i < 150; i++) {
            assertThat(queue.isAdmitted(visitors.get(i))).isFalse();
        }
    }

    @Test
    void admitNext_PartialAdmission_LessVisitorsThanSlots() {
        // Arrange
        queue.activate();
        List<BuyerReference> visitors = createBuyerReferences(50);
        for (BuyerReference visitor : visitors) {
            queue.enqueue(visitor);
        }

        // Act
        List<BuyerReference> newlyAdmitted = queue.admitNext(10);

        // Assert
        assertThat(newlyAdmitted)
                .hasSize(50)
                .containsExactlyElementsOf(visitors);
        
        // All should be admitted since queue size < max admissions
        for (BuyerReference visitor : visitors) {
            assertThat(queue.isAdmitted(visitor)).isTrue();
        }
    }

    @Test
    void admitNext_FullyAdmittedQueue_DoesNotAdmitMore() {
        // Arrange
        queue = new VirtualQueue(queueId, eventId, 50, 50);
        queue.activate();
        
        List<BuyerReference> first50 = createBuyerReferences(50);
        List<BuyerReference> next50 = createBuyerReferences(50, 50);
        
        for (BuyerReference visitor : first50) {
            queue.enqueue(visitor);
        }
        for (BuyerReference visitor : next50) {
            queue.enqueue(visitor);
        }

        // Act - First call admits 50
        List<BuyerReference> firstBatch = queue.admitNext(10);
        
        // Act - Second call should admit nothing (all slots full)
        List<BuyerReference> secondBatch = queue.admitNext(10);

        // Assert
        assertThat(firstBatch).hasSize(50);
        assertThat(secondBatch).isEmpty(); // No new admissions
        
        // Verify next 50 are still waiting
        for (BuyerReference visitor : next50) {
            assertThat(queue.isAdmitted(visitor)).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Test 3: isAdmitted() - Checking admission status
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void isAdmitted_AdmittedVisitor_ReturnsTrue() {
        // Arrange
        queue.activate();
        BuyerReference visitor = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");
        
        queue.enqueue(visitor);
        queue.admitNext(10);

        // Act
        boolean admitted = queue.isAdmitted(visitor);

        // Assert
        assertThat(admitted).isTrue();
    }

    @Test
    void isAdmitted_WaitingVisitor_ReturnsFalse() {
        // Arrange
        queue = new VirtualQueue(queueId, eventId, 50, 2); // Max 2 admissions
        queue.activate();
        
        BuyerReference visitor1 = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");
        BuyerReference visitor2 = new BuyerReference(BuyerType.MEMBER, "session-2", "member-2");
        BuyerReference visitor3 = new BuyerReference(BuyerType.MEMBER, "session-3", "member-3");
        
        queue.enqueue(visitor1);
        queue.enqueue(visitor2);
        queue.enqueue(visitor3);
        
        queue.admitNext(10); // Admits first 2

        // Act
        boolean admitted = queue.isAdmitted(visitor3);

        // Assert
        assertThat(admitted).isFalse();
    }

    @Test
    void isAdmitted_NeverEnqueued_ReturnsFalse() {
        // Arrange
        queue.activate();
        BuyerReference visitor = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");

        // Act
        boolean admitted = queue.isAdmitted(visitor);

        // Assert
        assertThat(admitted).isFalse();
    }

    @Test
    void isAdmitted_TokenExpired_ReturnsFalseAndPurgesToken() {
        // Arrange
        queue = new VirtualQueue(queueId, eventId, 50, 100);
        queue.activate();
        
        BuyerReference visitor = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");
        queue.enqueue(visitor);
        
        // Admit with very short validity (0 minutes)
        queue.admitNext(0);

        // Act - Wait for token to expire
        try {
            Thread.sleep(100); // 0 minute token expires immediately
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        boolean admitted = queue.isAdmitted(visitor);

        // Assert
        assertThat(admitted).isFalse();
    }

    @Test
    void revokeAdmission_RemovesAdmittedVisitor() {
        // Arrange
        queue.activate();
        BuyerReference visitor = new BuyerReference(BuyerType.MEMBER, "session-1", "member-1");
        
        queue.enqueue(visitor);
        queue.admitNext(10);
        
        assertThat(queue.isAdmitted(visitor)).isTrue();
        assertThat(queue.getOpenSlots()).isEqualTo(maxAdmissions - 1);

        // Act
        queue.revokeAdmission(visitor);

        // Assert
        assertThat(queue.isAdmitted(visitor)).isFalse();
        assertThat(queue.getOpenSlots()).isEqualTo(maxAdmissions); // Slot freed
    }

    @Test
    void expireTokens_RemovesExpiredTokensAndFreesSlots() {
        // Arrange
        queue = new VirtualQueue(queueId, eventId, 50, 5);
        queue.activate();
        
        List<BuyerReference> first5 = createBuyerReferences(5);
        List<BuyerReference> next5 = createBuyerReferences(5, 5);
        
        for (BuyerReference visitor : first5) {
            queue.enqueue(visitor);
        }
        for (BuyerReference visitor : next5) {
            queue.enqueue(visitor);
        }
        
        // Admit first 5 with 0 minute validity (expires immediately)
        queue.admitNext(0);
        
        // Verify next 5 are waiting (queue is full)
        for (BuyerReference visitor : next5) {
            assertThat(queue.isAdmitted(visitor)).isFalse();
        }
        
        // Act - Wait for tokens to expire then call admitNext again
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        List<BuyerReference> nowAdmitted = queue.admitNext(10);

        // Assert - Next 5 should now be admitted after first batch expired
        assertThat(nowAdmitted)
                .hasSize(5)
                .containsExactlyElementsOf(next5);
        
        for (BuyerReference visitor : next5) {
            assertThat(queue.isAdmitted(visitor)).isTrue();
        }
    }

    @Test
    void getOpenSlots_ReflectsCurrentLoad() {
        // Arrange
        queue = new VirtualQueue(queueId, eventId, 50, 100);
        queue.activate();
        
        List<BuyerReference> visitors = createBuyerReferences(30);
        for (BuyerReference visitor : visitors) {
            queue.enqueue(visitor);
        }

        // Act & Assert - Initially max slots available
        assertThat(queue.getOpenSlots()).isEqualTo(100);
        
        // Admit 30 visitors
        queue.admitNext(10);
        assertThat(queue.getOpenSlots()).isEqualTo(70);
        
        // Revoke one
        queue.revokeAdmission(visitors.get(0));
        assertThat(queue.getOpenSlots()).isEqualTo(71);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────────────

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
