package com.eventsystem.domain.queue;

import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class QueueValueObjectsTest {

    @Test
    void admissionToken_lifecycle_and_getters() throws Exception {
        BuyerReference ref = new BuyerReference(BuyerType.GUEST, "s1", null);
        AdmissionToken token = new AdmissionToken(ref, 10);

        assertThat(token.getBuyerRef()).isEqualTo(ref);
        assertThat(token.isConsumed()).isFalse();
        assertThat(token.isExpired()).isFalse();

        token.markConsumed();
        assertThat(token.isConsumed()).isTrue();

        // Test JPA constructor
        java.lang.reflect.Constructor<AdmissionToken> c = AdmissionToken.class.getDeclaredConstructor();
        c.setAccessible(true);
        AdmissionToken emptyToken = c.newInstance();
        assertThat(emptyToken.getBuyerRef()).isNull();
    }

    @Test
    void queueEntry_getters_and_jpaConstructor() throws Exception {
        BuyerReference ref = new BuyerReference(BuyerType.GUEST, "s1", null);
        QueueEntry entry = new QueueEntry(ref, 5);

        assertThat(entry.getVisitorRef()).isEqualTo(ref);
        assertThat(entry.getPosition()).isEqualTo(5);
        assertThat(entry.getJoinedAt()).isNotNull().isBeforeOrEqualTo(Instant.now());

        // Test JPA constructor
        java.lang.reflect.Constructor<QueueEntry> c = QueueEntry.class.getDeclaredConstructor();
        c.setAccessible(true);
        QueueEntry emptyEntry = c.newInstance();
        assertThat(emptyEntry.getVisitorRef()).isNull();
    }

    @Test
    void queueStatus_values_and_valueOf() {
        // This covers the implicit valueOf and values() methods of the Enum
        assertThat(QueueStatus.valueOf("ACTIVE")).isEqualTo(QueueStatus.ACTIVE);
        assertThat(QueueStatus.values()).containsExactly(QueueStatus.INACTIVE, QueueStatus.ACTIVE, QueueStatus.DRAINING);
    }
}