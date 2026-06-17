package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
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
    void crudAndQueries_workCorrectly() throws Exception {
        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, "sess1", "mem1");
        // הזמנה שתוקפה פג אתמול - משויכת לאירוע EV-1
        ActiveOrder expiredOrder = new ActiveOrder("ORD-1", buyer, "EV-1", Instant.now().minus(1, ChronoUnit.DAYS));
        
        // התיקון: נשנה את שדה הסטטוס הפנימי ל-CHECKED_OUT כדי ש-findExpiredReservations ימצא אותה
        java.lang.reflect.Field statusField = ActiveOrder.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(expiredOrder, com.eventsystem.domain.order.OrderStatus.CHECKED_OUT);

        // הזמנה בתוקף למחר - משויכת לאירוע EV-2 כדי למנוע כפילות
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

        // findExpiredReservations - עכשיו זה יעבור ב-100% כי הסטטוס מתאים!
        List<ActiveOrder> expiredRes = repository.findExpiredReservations();
        assertThat(expiredRes).hasSize(1);
        assertThat(expiredRes.get(0).getOrderId()).isEqualTo("ORD-1");

        // findExpired
        Optional<List<ActiveOrder>> expiredOpt = repository.findExpired();
        assertThat(expiredOpt).isPresent();
        assertThat(expiredOpt.get()).hasSize(1);

        // Delete
        repository.delete("ORD-1");
        em.flush();
        em.clear();
        assertThat(repository.findById("ORD-1")).isEmpty();
    }

    @Test
    void findExpired_whenNoneExpired_returnsEmptyOptional() {
        // אין הזמנות במסד הנתונים
        Optional<List<ActiveOrder>> expiredOpt = repository.findExpired();
        assertThat(expiredOpt).isEmpty();
    }
}