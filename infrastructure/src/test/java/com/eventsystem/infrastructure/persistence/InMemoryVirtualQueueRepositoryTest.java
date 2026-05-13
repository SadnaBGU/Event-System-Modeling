package com.eventsystem.infrastructure.persistence;

import com.eventsystem.domain.queue.VirtualQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryVirtualQueueRepositoryTest {

    private InMemoryVirtualQueueRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryVirtualQueueRepository();
    }

    @Test
    void saveAndFindByEvent_WorksCorrectly() {
        // Arrange
        String eventId = "EVENT-555";
        String queueId = "QUEUE-123";
        
        VirtualQueue queue = mock(VirtualQueue.class);
        when(queue.getQueueId()).thenReturn(queueId);
        when(queue.getEventId()).thenReturn(eventId);

        // Act
        repository.save(queue);
        Optional<VirtualQueue> result = repository.findByEvent(eventId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(queue, result.get());
    }
}