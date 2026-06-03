package com.eventsystem.domain.policy;

import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.policy.discount.DiscountSummary;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscountSummaryTest {

        // TST-07 / DP-14: visible discount summary exposes name, percent, actual
        // amount, and total.
        @Test
        void getSpecificDiscountInfo_byIndexAndName_returnsReadableDiscountDetails() {
                DiscountSummary summary = new DiscountSummary(
                                List.of("Early bird", "Student"),
                                List.of(BigDecimal.TEN, BigDecimal.valueOf(15)),
                                List.of(BigDecimal.valueOf(20), BigDecimal.valueOf(30)),
                                BigDecimal.valueOf(50),
                                false);

                assertThat(summary.getSpecificDiscountPurchaseSummary(0)).isEqualTo("Early bird : 10 : 20");
                assertThat(summary.getSpecificDiscountPurchaseSummary("Student")).isEqualTo("Student : 15 : 30");
        }

        @Test
        void getSpecificDiscountInfo_whenIndexInvalid_throwsIndexOutOfBounds() {
                DiscountSummary summary = DiscountSummary.noDiscountSummary();

                assertThatThrownBy(() -> summary.getSpecificDiscountPurchaseSummary(0))
                                .isInstanceOf(IndexOutOfBoundsException.class)
                                .hasMessageContaining("Invalid discount index");
        }

        @Test
        void getSpecificDiscountInfo_whenNameWasNotApplied_throwsDiscountPolicyException() {
                DiscountSummary summary = new DiscountSummary(
                                List.of("Early bird"),
                                List.of(BigDecimal.TEN),
                                List.of(BigDecimal.valueOf(20)),
                                BigDecimal.valueOf(20),
                                false);

                assertThatThrownBy(() -> summary.getSpecificDiscountPurchaseSummary("Coupon"))
                                .isInstanceOf(DiscountPolicyException.class)
                                .hasMessageContaining("Discount was not applied");
        }

        @Test
        void constructorCopiesInputListsDefensively() {
                List<String> names = new ArrayList<>(List.of("Visible"));
                List<BigDecimal> percents = new ArrayList<>(List.of(BigDecimal.TEN));
                List<BigDecimal> amounts = new ArrayList<>(List.of(BigDecimal.valueOf(20)));

                DiscountSummary summary = new DiscountSummary(names, percents, amounts, BigDecimal.valueOf(20), false);

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
                                BigDecimal.ONE,
                                false)).isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("same size");
        }

        @Test
        void isDiscountCappedAt100_whenPercentsReachOrExceed100_returnsTrue() {
                DiscountSummary capped = new DiscountSummary(
                                List.of("A", "B"),
                                List.of(BigDecimal.valueOf(60), BigDecimal.valueOf(40)),
                                List.of(BigDecimal.ZERO, BigDecimal.ZERO),
                                BigDecimal.ZERO,
                                false);

                assertThat(capped.isDiscountCappedAt100()).isTrue();
        }

        @Test
        void isDiscountCappedAt100_whenPercentsBelow100_returnsFalse() {
                DiscountSummary notCapped = new DiscountSummary(
                                List.of("A", "B"),
                                List.of(BigDecimal.valueOf(20), BigDecimal.valueOf(30)),
                                List.of(BigDecimal.ZERO, BigDecimal.ZERO),
                                BigDecimal.ZERO,
                                false);

                assertThat(notCapped.isDiscountCappedAt100()).isFalse();
        }

        @Test
        void replaceActualAmounts_returnsNewSummaryWithSameDiscountsAndNewAmounts() {
                DiscountSummary original = new DiscountSummary(
                                List.of("Visible"),
                                List.of(BigDecimal.TEN),
                                List.of(BigDecimal.ZERO),
                                BigDecimal.ZERO,
                                false);

                DiscountSummary replaced = original.replaceActualAmounts(
                                List.of(BigDecimal.valueOf(25)),
                                BigDecimal.valueOf(25));

                assertThat(replaced.appliedDiscountsNames()).containsExactly("Visible");
                assertThat(replaced.appliedDiscountPercents()).containsExactly(BigDecimal.TEN);
                assertThat(replaced.actualDiscountAmount()).containsExactly(BigDecimal.valueOf(25));
                assertThat(replaced.totalDiscount()).isEqualByComparingTo("25");
        }

        // DP-13:
        // Summary should mark cappedAt100Percent when stacked percents reach or exceed
        // 100.
        @Test
        void constructor_whenPercentsSumToMoreThan100_shouldStoreCappedFlag_DP13() {
                DiscountSummary summary = new DiscountSummary(
                                List.of("A", "B"),
                                List.of(BigDecimal.valueOf(60), BigDecimal.valueOf(50)),
                                List.of(BigDecimal.ZERO, BigDecimal.ZERO),
                                BigDecimal.ZERO,
                                false);

                assertThat(summary.cappedAt100Percent()).isTrue();
                assertThat(summary.isDiscountCappedAt100()).isTrue();
        }

        // DP-13:
        // Summary should not mark cappedAt100Percent below 100.
        @Test
        void constructor_whenPercentsSumBelow100_shouldNotStoreCappedFlag_DP13() {
                DiscountSummary summary = new DiscountSummary(
                                List.of("A", "B"),
                                List.of(BigDecimal.valueOf(20), BigDecimal.valueOf(30)),
                                List.of(BigDecimal.ZERO, BigDecimal.ZERO),
                                BigDecimal.ZERO,
                                true);

                assertThat(summary.cappedAt100Percent()).isFalse();
                assertThat(summary.isDiscountCappedAt100()).isFalse();
        }

        // DP-13:
        // Replacing actual amounts should keep capped calculation based on the same
        // percents.
        @Test
        void replaceActualAmounts_shouldPreserveCappedFlag_DP13() {
                DiscountSummary summary = new DiscountSummary(
                                List.of("A", "B"),
                                List.of(BigDecimal.valueOf(70), BigDecimal.valueOf(40)),
                                List.of(BigDecimal.ZERO, BigDecimal.ZERO),
                                BigDecimal.ZERO,
                                false);

                DiscountSummary replaced = summary.replaceActualAmounts(
                                List.of(BigDecimal.valueOf(70), BigDecimal.valueOf(40)),
                                BigDecimal.valueOf(100));

                assertThat(replaced.cappedAt100Percent()).isTrue();
                assertThat(replaced.totalDiscount()).isEqualByComparingTo("100");
        }

        // DP-13:
        // noDiscountSummary should represent no applied discount.
        @Test
        void noDiscountSummary_shouldBeEmptyAndZero() {
                DiscountSummary summary = DiscountSummary.noDiscountSummary();

                assertThat(summary.appliedDiscountsNames()).isEmpty();
                assertThat(summary.appliedDiscountPercents()).isEmpty();
                assertThat(summary.actualDiscountAmount()).isEmpty();
                assertThat(summary.totalDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(summary.cappedAt100Percent()).isFalse();
        }

        // DP-13:
        // replaceActualAmounts should preserve discount names/percents and update
        // actual money values.
        @Test
        void replaceActualAmounts_shouldReturnNewSummaryWithUpdatedAmounts() {
                DiscountSummary original = new DiscountSummary(
                                List.of("A", "B"),
                                List.of(BigDecimal.valueOf(10), BigDecimal.valueOf(15)),
                                List.of(BigDecimal.ZERO, BigDecimal.ZERO),
                                BigDecimal.ZERO,
                                false);

                DiscountSummary replaced = original.replaceActualAmounts(
                                List.of(BigDecimal.valueOf(20), BigDecimal.valueOf(30)),
                                BigDecimal.valueOf(50));

                assertThat(replaced.appliedDiscountsNames()).containsExactly("A", "B");
                assertThat(replaced.appliedDiscountPercents())
                                .containsExactly(BigDecimal.valueOf(10), BigDecimal.valueOf(15));
                assertThat(replaced.actualDiscountAmount())
                                .containsExactly(BigDecimal.valueOf(20), BigDecimal.valueOf(30));
                assertThat(replaced.totalDiscount()).isEqualByComparingTo("50");
        }

        // DP-13:
        // Constructor should compute capped flag from percent list, ignoring supplied
        // boolean.
        @Test
        void constructor_shouldComputeCappedFlagFromPercents() {
                DiscountSummary capped = new DiscountSummary(
                                List.of("A", "B"),
                                List.of(BigDecimal.valueOf(70), BigDecimal.valueOf(40)),
                                List.of(BigDecimal.ZERO, BigDecimal.ZERO),
                                BigDecimal.ZERO,
                                false);

                DiscountSummary notCapped = new DiscountSummary(
                                List.of("A", "B"),
                                List.of(BigDecimal.valueOf(20), BigDecimal.valueOf(30)),
                                List.of(BigDecimal.ZERO, BigDecimal.ZERO),
                                BigDecimal.ZERO,
                                true);

                assertThat(capped.cappedAt100Percent()).isTrue();
                assertThat(notCapped.cappedAt100Percent()).isFalse();
        }
}
