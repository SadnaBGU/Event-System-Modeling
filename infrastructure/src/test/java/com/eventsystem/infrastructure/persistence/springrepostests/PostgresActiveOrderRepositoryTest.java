package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderStatus;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresActiveOrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresActiveOrderRepository.class)
class PostgresActiveOrderRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresActiveOrderRepository repository;
    @Autowired
    private EntityManager em;

    @Test
    void crudAndQueries_workCorrectly() {
        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, "sess1", "mem1");
        // ACTIVE reservation whose timer expired yesterday — event EV-1
        ActiveOrder expiredOrder = new ActiveOrder("ORD-1", buyer, "EV-1", Instant.now().minus(1, ChronoUnit.DAYS));
        // ACTIVE reservation valid until tomorrow — event EV-2 (avoids buyer+event duplicate)
        ActiveOrder validOrder = new ActiveOrder("ORD-2", buyer, "EV-2", Instant.now().plus(1, ChronoUnit.DAYS));

        repository.save(expiredOrder);
        repository.save(validOrder);
        em.flush();
        em.clear();

        // findById
        assertThat(repository.findById("ORD-1")).isPresent();

        // findByEvent
        assertThat(repository.findByEvent("EV-1")).hasSize(1);
        assertThat(repository.findByEvent("EV-2")).hasSize(1);

        // findByBuyerAndEvent
        assertThat(repository.findByBuyerAndEvent(buyer, "EV-1")).isPresent();
        assertThat(repository.findByBuyerAndEvent(buyer, "EV-2")).isPresent();

        // findExpired — only the expired ACTIVE order is returned
        Optional<List<ActiveOrder>> expiredOpt = repository.findExpired();
        assertThat(expiredOpt).isPresent();
        assertThat(expiredOpt.get()).hasSize(1);
        assertThat(expiredOpt.get().get(0).getOrderId()).isEqualTo("ORD-1");

        // Delete
        repository.delete("ORD-1");
        em.flush();
        em.clear();
        assertThat(repository.findById("ORD-1")).isEmpty();
    }

    @Test
    void findExpired_ignoresNonActiveAndFutureReservations() throws Exception {
        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, "sess1", "mem1");

        // ACTIVE + past expiry → should be swept
        ActiveOrder expiredActive = new ActiveOrder("ORD-A", buyer, "EV-A", Instant.now().minus(1, ChronoUnit.DAYS));
        // CHECKED_OUT + past expiry → already completed, must NOT be swept again
        ActiveOrder checkedOutPast = new ActiveOrder("ORD-B", buyer, "EV-B", Instant.now().minus(1, ChronoUnit.DAYS));
        setStatus(checkedOutPast, OrderStatus.CHECKED_OUT);
        // ACTIVE but not yet expired → must NOT be swept
        ActiveOrder activeFuture = new ActiveOrder("ORD-C", buyer, "EV-C", Instant.now().plus(1, ChronoUnit.DAYS));

        repository.save(expiredActive);
        repository.save(checkedOutPast);
        repository.save(activeFuture);
        em.flush();
        em.clear();

        Optional<List<ActiveOrder>> expired = repository.findExpired();

        assertThat(expired).isPresent();
        assertThat(expired.get())
                .extracting(ActiveOrder::getOrderId)
                .containsExactly("ORD-A");
    }

    @Test
    void findExpired_whenNoneExpired_returnsPresentEmptyList() {
        Optional<List<ActiveOrder>> expiredOpt = repository.findExpired();

        // Always a present Optional so "nothing to sweep" is a normal outcome, not an error.
        assertThat(expiredOpt).isPresent();
        assertThat(expiredOpt.get()).isEmpty();
    }

    private void setStatus(ActiveOrder order, OrderStatus status) throws Exception {
        java.lang.reflect.Field statusField = ActiveOrder.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(order, status);
    }
}
