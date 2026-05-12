package com.eventsystem.domain.zone;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ZoneIdTest {

    @Test
    void random_generatesDifferentIds() {
        ZoneId a = ZoneId.random();
        ZoneId b = ZoneId.random();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void constructor_rejectsNullValue() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ZoneId(null))
                .withMessageContaining("value must not be null");
    }

    @Test
    void constructor_rejectsBlankValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ZoneId("  "))
                .withMessageContaining("value must not be blank");
    }

    @Test
    void sameValue_isEqual() {
        ZoneId a = new ZoneId("zone-123");
        ZoneId b = new ZoneId("zone-123");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void value_returnsConstructorArgument() {
        assertThat(new ZoneId("abc").value()).isEqualTo("abc");
    }
}
