package com.eventsystem.application.order;

import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.application.appexceptions.QueueAdmissionRequiredException;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.IActiveOrderRepository;
import com.eventsystem.domain.queue.AdmissionToken;
import com.eventsystem.domain.queue.IVirtualQueueRepository;
import com.eventsystem.domain.queue.QueueStatus;
import com.eventsystem.domain.queue.VirtualQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private IVirtualQueueRepository queueRepository;

    @Mock
    private INotificationPort notificationPort;

    @Mock
    private IActiveOrderRepository activeOrderRepository;

    @InjectMocks
    private QueueService queueService;

    private final String EVENT_ID = "EVENT-999";
    private BuyerReference testBuyer;
    private VirtualQueue mockQueue;

    @BeforeEach
    void setUp() {
        testBuyer = new BuyerReference(BuyerType.MEMBER, "sess-1", "user-123");
        mockQueue = mock(VirtualQueue.class);
        lenient().when(mockQueue.getQueueId()).thenReturn("Q-123");
        lenient().when(mockQueue.getStatus()).thenReturn(QueueStatus.ACTIVE);
        lenient().when(activeOrderRepository.countActiveNonExpiredByEvent(any(), any())).thenReturn(0L);
    }

    @Test
    void requireAdmissionOrEnqueueOnHighLoad_underThreshold_doesNothing() {
        // Threshold is 3 concurrent buyers, so 2 active orders is still under load.
        when(activeOrderRepository.countActiveNonExpiredByEvent(eq(EVENT_ID), any())).thenReturn(2L);

        queueService.requireAdmissionOrEnqueueOnHighLoad(EVENT_ID, testBuyer);

        verify(queueRepository, never()).findByEvent(any());
        verify(queueRepository, never()).save(any());
    }

    @Test
    void requireAdmissionOrEnqueueOnHighLoad_overThreshold_notAdmitted_throwsQueueRequired() {
        // 3 active orders reaches the concurrency limit, so the 4th buyer must queue.
        when(activeOrderRepository.countActiveNonExpiredByEvent(eq(EVENT_ID), any())).thenReturn(3L);
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.of(mockQueue));
        when(mockQueue.isAdmitted(testBuyer)).thenReturn(false);

        assertThrows(QueueAdmissionRequiredException.class,
                () -> queueService.requireAdmissionOrEnqueueOnHighLoad(EVENT_ID, testBuyer));

        verify(mockQueue).joinQueue(testBuyer);
        verify(queueRepository).save(mockQueue);
    }

    @Test
    void requireAdmissionOrEnqueueOnHighLoad_overThreshold_admitted_proceedsHoldingSlot() {
        when(activeOrderRepository.countActiveNonExpiredByEvent(eq(EVENT_ID), any())).thenReturn(3L);
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.of(mockQueue));
        when(mockQueue.isAdmitted(testBuyer)).thenReturn(true);

        assertDoesNotThrow(() -> queueService.requireAdmissionOrEnqueueOnHighLoad(EVENT_ID, testBuyer));

        // An admitted buyer keeps their admission slot for the whole purchase: the
        // token is NOT released and nobody new is admitted at purchase start.
        verify(mockQueue, never()).consumeTokenFor(any());
        verify(mockQueue, never()).admitNextGroup(anyInt());
        verify(notificationPort, never()).sendQueueTurnArrived(any(), any());
    }

    @Test
    void releaseAdmissionAndAdmitNext_finishedBuyer_freesSlotAndAdmitsNext() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.of(mockQueue));

        BuyerReference nextBuyer = new BuyerReference(BuyerType.MEMBER, "sess-2", "user-456");
        AdmissionToken nextToken = mock(AdmissionToken.class);
        when(nextToken.getBuyerRef()).thenReturn(nextBuyer);
        when(mockQueue.admitNextGroup(anyInt())).thenReturn(List.of(nextToken));

        queueService.releaseAdmissionAndAdmitNext(EVENT_ID, testBuyer);

        // The finished buyer's token is taken back first, then the next waiting buyer
        // is admitted into the freed slot and notified.
        verify(mockQueue).revokeAdmission(testBuyer);
        verify(mockQueue).admitNextGroup(anyInt());
        verify(queueRepository).save(mockQueue);
        verify(notificationPort).sendQueueTurnArrived(nextBuyer, EVENT_ID);
    }

    @Test
    void releaseAdmissionAndAdmitNext_noQueueExists_doesNothing() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.empty());

        queueService.releaseAdmissionAndAdmitNext(EVENT_ID, testBuyer);

        verify(queueRepository, never()).save(any());
        verify(notificationPort, never()).sendQueueTurnArrived(any(), any());
    }

    @Test
    void requireAdmissionOrEnqueueOnHighLoad_overThreshold_inactiveQueue_reactivatesAndThrowsQueueRequired() {
        when(activeOrderRepository.countActiveNonExpiredByEvent(eq(EVENT_ID), any())).thenReturn(3L);
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.of(mockQueue));
        when(mockQueue.getStatus()).thenReturn(QueueStatus.INACTIVE);
        when(mockQueue.isAdmitted(testBuyer)).thenReturn(false);

        assertThrows(QueueAdmissionRequiredException.class,
                () -> queueService.requireAdmissionOrEnqueueOnHighLoad(EVENT_ID, testBuyer));

        verify(mockQueue).activate();
        verify(mockQueue).joinQueue(testBuyer);
        verify(queueRepository).save(mockQueue);
    }

    @Test
    void enqueueVisitor_QueueExists_DelegatesToQueue() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.of(mockQueue));

        queueService.enqueueVisitor(EVENT_ID, testBuyer);

        verify(mockQueue).joinQueue(testBuyer);
        verify(queueRepository).save(mockQueue);
    }

    @Test
    void enqueueVisitor_NoQueueExists_CreatesNewQueueAndEnqueues() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.empty());

        queueService.enqueueVisitor(EVENT_ID, testBuyer);

        // נוודא שהוא מייצר ושומר תור חדש מכיוון שלא היה אחד
        verify(queueRepository).save(any(VirtualQueue.class));
        // מכיוון שהוא מפעיל את joinQueue על אובייקט חדש שיצר פנימית, 
        // אנו לא יכולים לנטר את mockQueue פה, אלא את העובדה שה-save אכן נקרא.
    }

    @Test
    void processNextBatch_QueueExists_AdmitsAndNotifies() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.of(mockQueue));
        AdmissionToken token = mock(AdmissionToken.class);
        when(token.getBuyerRef()).thenReturn(testBuyer);
        
        when(mockQueue.admitNextGroup(anyInt())).thenReturn(List.of(token));

        queueService.processNextBatch(EVENT_ID);

        verify(queueRepository).save(mockQueue);
        verify(notificationPort).sendQueueTurnArrived(testBuyer, EVENT_ID);
    }

    @Test
    void processNextBatch_NoQueueExists_DoesNothing() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.empty());

        queueService.processNextBatch(EVENT_ID);

        verify(queueRepository, never()).save(any());
        verify(notificationPort, never()).sendQueueTurnArrived(any(), any());
    }

    @Test
    void getAdmissionStatus_QueueExistsAndAdmitted_ReturnsStatusObject() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.of(mockQueue));
        when(mockQueue.isAdmitted(testBuyer)).thenReturn(true);
        when(mockQueue.positionOf(testBuyer)).thenReturn(0);

        QueueService.AdmissionStatus result = queueService.getAdmissionStatus(EVENT_ID, testBuyer);

        assertTrue(result.isAdmitted);
        assertEquals(0, result.position);
    }

    @Test
    void getAdmissionStatus_NoQueueExists_ReturnsFalseAndMinusOne() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.empty());

        QueueService.AdmissionStatus result = queueService.getAdmissionStatus(EVENT_ID, testBuyer);

        assertFalse(result.isAdmitted);
        assertEquals(-1, result.position);
    }

    @Test
    void handleEventSoldOut_ClearsQueueAndNotifiesWaiting() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.of(mockQueue));
        BuyerReference waitingBuyer = new BuyerReference(BuyerType.MEMBER, "sess-2", "user-456");
        when(mockQueue.clearQueue()).thenReturn(List.of(waitingBuyer));

        queueService.handleEventSoldOut(EVENT_ID);

        verify(mockQueue).clearQueue();
        verify(queueRepository).save(mockQueue);
        verify(notificationPort).sendEventSoldOut(waitingBuyer, EVENT_ID);
    }

    @Test
    void handleEventSoldOut_NoQueueExists_DoesNothing() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.empty());

        queueService.handleEventSoldOut(EVENT_ID);

        verify(queueRepository, never()).save(any());
        verify(notificationPort, never()).sendEventSoldOut(any(), any());
    }
}