package com.eventsystem.domain.shared;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void constructor_rejectsNullAmount() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Money(null, "ILS"));
    }

    @Test
    void constructor_rejectsNegativeAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(new BigDecimal("-0.01"), "ILS"))
                .withMessageContaining("must not be negative");
    }

    @Test
    void constructor_rejectsNullCurrency() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Money(BigDecimal.TEN, null));
    }

    @Test
    void constructor_rejectsBlankCurrency() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(BigDecimal.TEN, "  "))
                .withMessageContaining("currency must not be blank");
    }

    @Test
    void zeroAmount_isValid() {
        assertThatNoException().isThrownBy(() -> new Money(BigDecimal.ZERO, "ILS"));
    }

    @Test
    void add_sameCurrency_returnsSum() {
        Money a = new Money(new BigDecimal("10.00"), "ILS");
        Money b = new Money(new BigDecimal("5.50"), "ILS");
        assertThat(a.add(b).amount()).isEqualByComparingTo("15.50");
        assertThat(a.add(b).currency()).isEqualTo("ILS");
    }

    @Test
    void add_differentCurrencies_throws() {
        Money ils = new Money(BigDecimal.TEN, "ILS");
        Money usd = new Money(BigDecimal.ONE, "USD");
        assertThatIllegalArgumentException().isThrownBy(() -> ils.add(usd));
    }

    @Test
    void multiply_returnsScaledAmount() {
        Money price = new Money(new BigDecimal("12.00"), "ILS");
        assertThat(price.multiply(3).amount()).isEqualByComparingTo("36.00");
    }

    @Test
    void multiply_byZero_returnsZero() {
        Money price = new Money(new BigDecimal("50.00"), "ILS");
        assertThat(price.multiply(0).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void multiply_negativeFactory_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Money(BigDecimal.TEN, "ILS").multiply(-1))
                .withMessageContaining("must not be negative");
    }

    @Test
    void of_factoryMethod_createsEquivalentInstance() {
        Money via_new = new Money(new BigDecimal("20.00"), "ILS");
        Money via_of  = Money.of(new BigDecimal("20.00"), "ILS");
        assertThat(via_new).isEqualTo(via_of);
    }
}
