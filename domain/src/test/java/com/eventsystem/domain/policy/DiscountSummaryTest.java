package com.eventsystem.domain.policy;

import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscountSummaryTest {

    // TST-07 / DP-14: visible discount summary exposes name, percent, actual amount, and total.
    @Test
    void getSpecificDiscountInfo_byIndexAndName_returnsReadableDiscountDetails() {
        DiscountSummary summary = new DiscountSummary(
                List.of("Early bird", "Student"),
                List.of(BigDecimal.TEN, BigDecimal.valueOf(15)),
                List.of(BigDecimal.valueOf(20), BigDecimal.valueOf(30)),
                BigDecimal.valueOf(50)
        );

        assertThat(summary.getSpecificDiscountInfo(0)).isEqualTo("Early bird : 10 : 20");
        assertThat(summary.getSpecificDiscountInfo("Student")).isEqualTo("Student : 15 : 30");
    }

    @Test
    void getSpecificDiscountInfo_whenIndexInvalid_throwsIndexOutOfBounds() {
        DiscountSummary summary = DiscountSummary.NoDiscountSummary();

        assertThatThrownBy(() -> summary.getSpecificDiscountInfo(0))
                .isInstanceOf(IndexOutOfBoundsException.class)
                .hasMessageContaining("Invalid discount index");
    }

    @Test
    void getSpecificDiscountInfo_whenNameWasNotApplied_throwsDiscountPolicyException() {
        DiscountSummary summary = new DiscountSummary(
                List.of("Early bird"),
                List.of(BigDecimal.TEN),
                List.of(BigDecimal.valueOf(20)),
                BigDecimal.valueOf(20)
        );

        assertThatThrownBy(() -> summary.getSpecificDiscountInfo("Coupon"))
                .isInstanceOf(DiscountPolicyException.class)
                .hasMessageContaining("Discount was not applied");
    }

    @Test
    void constructorCopiesInputListsDefensively() {
        List<String> names = new ArrayList<>(List.of("Visible"));
        List<BigDecimal> percents = new ArrayList<>(List.of(BigDecimal.TEN));
        List<BigDecimal> amounts = new ArrayList<>(List.of(BigDecimal.valueOf(20)));

        DiscountSummary summary = new DiscountSummary(names, percents, amounts, BigDecimal.valueOf(20));

        names.add("Late mutation");
        percents.add(BigDecimal.valueOf(99));
        amounts.add(BigDecimal.valueOf(99));

        assertThat(summary.appliedDiscountsNames()).containsExactly("Visible");
        assertThat(summary.appliedDiscountPercents()).containsExactly(BigDecimal.TEN);
        assertThat(summary.actualDiscountAmount()).containsExactly(BigDecimal.valueOf(20));
        assertThatThrownBy(() -> summary.appliedDiscountsNames().add("blocked"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructorRejectsMismatchedListSizes() {
        assertThatThrownBy(() -> new DiscountSummary(
                List.of("A"),
                List.of(BigDecimal.TEN, BigDecimal.ONE),
                List.of(BigDecimal.ONE),
                BigDecimal.ONE
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("same size");
    }

    @Test
    void isDiscountCappedAt100_whenPercentsReachOrExceed100_returnsTrue() {
        DiscountSummary capped = new DiscountSummary(
                List.of("A", "B"),
                List.of(BigDecimal.valueOf(60), BigDecimal.valueOf(40)),
                List.of(BigDecimal.ZERO, BigDecimal.ZERO),
                BigDecimal.ZERO
        );

        assertThat(capped.isDiscountCappedAt100()).isTrue();
    }

    @Test
    void isDiscountCappedAt100_whenPercentsBelow100_returnsFalse() {
        DiscountSummary notCapped = new DiscountSummary(
                List.of("A", "B"),
                List.of(BigDecimal.valueOf(20), BigDecimal.valueOf(30)),
                List.of(BigDecimal.ZERO, BigDecimal.ZERO),
                BigDecimal.ZERO
        );

        assertThat(notCapped.isDiscountCappedAt100()).isFalse();
    }

    @Test
    void replaceActualAmounts_returnsNewSummaryWithSameDiscountsAndNewAmounts() {
        DiscountSummary original = new DiscountSummary(
                List.of("Visible"),
                List.of(BigDecimal.TEN),
                List.of(BigDecimal.ZERO),
                BigDecimal.ZERO
        );

        DiscountSummary replaced = original.ReplaceActualAmounts(
                List.of(BigDecimal.valueOf(25)),
                BigDecimal.valueOf(25)
        );

        assertThat(replaced.appliedDiscountsNames()).containsExactly("Visible");
        assertThat(replaced.appliedDiscountPercents()).containsExactly(BigDecimal.TEN);
        assertThat(replaced.actualDiscountAmount()).containsExactly(BigDecimal.valueOf(25));
        assertThat(replaced.totalDiscount()).isEqualByComparingTo("25");
    }
}
