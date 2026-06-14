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
    }

    @Test
    void enqueueVisitor_ExistingQueue_AddsVisitorAndSaves() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.of(mockQueue));

        queueService.enqueueVisitor(EVENT_ID, testBuyer);

        verify(mockQueue, times(1)).joinQueue(testBuyer);
        verify(queueRepository, times(1)).save(mockQueue);
    }

    @Test
    void enqueueVisitor_NoQueueExists_CreatesNewQueueAndSaves() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.empty());

        queueService.enqueueVisitor(EVENT_ID, testBuyer);

        verify(queueRepository, times(1)).save(any(VirtualQueue.class));
    }

    @Test
    void processNextBatch_QueueExists_AdmitsAndNotifies() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.of(mockQueue));
        
        BuyerReference buyer2 = new BuyerReference(BuyerType.MEMBER, "sess-2", "user-456");
        AdmissionToken token1 = new AdmissionToken(testBuyer, 10);
        AdmissionToken token2 = new AdmissionToken(buyer2, 10);
        when(mockQueue.admitNextGroup(anyInt())).thenReturn(List.of(token1, token2));

        queueService.processNextBatch(EVENT_ID);

        verify(mockQueue, times(1)).admitNextGroup(anyInt());
        verify(queueRepository, times(1)).save(mockQueue);
        
        verify(notificationPort, times(1)).sendQueueTurnArrived(testBuyer, EVENT_ID);
        verify(notificationPort, times(1)).sendQueueTurnArrived(buyer2, EVENT_ID);
    }

    @Test
    void processNextBatch_NoQueueExists_DoesNothing() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.empty());

        queueService.processNextBatch(EVENT_ID);

        verify(queueRepository, never()).save(any());
        verify(notificationPort, never()).sendQueueTurnArrived(any(), any());
    }

    @Test
    void checkAdmissionStatus_QueueExistsAndAdmitted_ReturnsTrue() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.of(mockQueue));
        when(mockQueue.isAdmitted(testBuyer)).thenReturn(true);

        boolean result = queueService.checkAdmissionStatus(EVENT_ID, testBuyer);

        assertTrue(result);
    }

    @Test
    void checkAdmissionStatus_NoQueueExists_ReturnsFalse() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.empty());

        boolean result = queueService.checkAdmissionStatus(EVENT_ID, testBuyer);

        assertFalse(result);
    }

    @Test
    void handleEventSoldOut_ClearsQueueAndNotifiesWaiting() {
        when(queueRepository.findByEvent(EVENT_ID)).thenReturn(Optional.of(mockQueue));
        BuyerReference waitingBuyer = new BuyerReference(BuyerType.MEMBER, "sess-2", "user-456");
        when(mockQueue.clearQueue()).thenReturn(List.of(waitingBuyer));

        queueService.handleEventSoldOut(EVENT_ID);

        verify(mockQueue, times(1)).clearQueue();
        verify(queueRepository, times(1)).save(mockQueue);
        verify(notificationPort, times(1)).sendEventSoldOut(waitingBuyer, EVENT_ID);
    }
}