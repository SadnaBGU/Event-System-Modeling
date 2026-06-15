package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.queue.VirtualQueue;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresVirtualQueueRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresVirtualQueueRepository.class)
class PostgresVirtualQueueRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresVirtualQueueRepository repository;

    @Autowired
    private EntityManager em;

    private VirtualQueue queue;
    private final String eventId = "EVT-TEST-1";

    @BeforeEach
    void setUp() {
        queue = new VirtualQueue("Q-100", eventId, 100, 50);
        queue.activate();
    }

    @Test
    void saveAndFindById_savesAllNestedCollections() {
        BuyerReference b1 = new BuyerReference(BuyerType.MEMBER, "sess-1", "user-1");
        BuyerReference b2 = new BuyerReference(BuyerType.MEMBER, "sess-2", "user-2");
        BuyerReference b3 = new BuyerReference(BuyerType.GUEST, "sess-3", "guest-3");

        queue.joinQueue(b1);
        queue.joinQueue(b2);
        queue.joinQueue(b3);
        
        queue.admitNextGroup(15); 

        repository.save(queue);
        em.flush();
        em.clear();

        Optional<VirtualQueue> foundOpt = repository.findById("Q-100");
        assertThat(foundOpt).isPresent();
        VirtualQueue found = foundOpt.get();

        assertThat(found.getEventId()).isEqualTo(eventId);
        assertThat(found.isAdmitted(b1)).isTrue();
        assertThat(found.isAdmitted(b2)).isTrue();
        
        assertThat(found.isAdmitted(b3)).isTrue(); 
    }

    @Test
    void findByEventId_returnsCorrectQueue() {
        repository.save(queue);
        
        VirtualQueue q2 = new VirtualQueue("Q-200", "EVT-OTHER", 100, 50);
        repository.save(q2);

        em.flush();
        em.clear();

        Optional<VirtualQueue> found = repository.findByEvent(eventId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo("Q-100");
    }
}