package com.eventsystem.application.order;

import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.queue.AdmissionToken;
import com.eventsystem.domain.queue.IVirtualQueueRepository;
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