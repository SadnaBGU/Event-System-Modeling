package com.eventsystem.application.policy.policybuilder;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.Discount;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PolicyScope;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.zone.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class PolicyCommandAssemblerTest {

    private PolicyCommandAssembler assembler;

    private static final EventId EVENT_ID = new EventId("event-1");
    private static final CompanyId COMPANY_ID = new CompanyId("company-1");

    private static final ZoneId REGULAR_ZONE = new ZoneId("regular-zone");
    private static final ZoneId VIP_ZONE = new ZoneId("vip-zone");

    @BeforeEach
    void setUp() {
        assembler = new PolicyCommandAssembler();
    }

    private PurchaseContext context(int ticketCount, LocalDate buyerBirthDate) {
        return new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                repeatedZones(REGULAR_ZONE, ticketCount),
                buyerBirthDate,
                null
        );
    }

    private PurchaseContext context(List<ZoneId> zones, LocalDate buyerBirthDate, String discountCode) {
        return new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                zones,
                buyerBirthDate,
                discountCode
        );
    }

    private List<ZoneId> repeatedZones(ZoneId zoneId, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> zoneId)
                .toList();
    }

    private LocalDate age(int years) {
        return LocalDate.now().minusYears(years);
    }

    private PolicyRuleCommand rule(String type) {
        return new PolicyRuleCommand(type, null, null, null, null, null);
    }

    private PolicyRuleCommand ruleWithValue(String type, int value) {
        return new PolicyRuleCommand(type, value, null, null, null, null);
    }

    private PolicyRuleCommand ruleWithDate(String type, LocalDate date) {
        return new PolicyRuleCommand(type, null, null, date.toString(), null, null);
    }

    private PolicyRuleCommand ruleWithCode(String code) {
        return new PolicyRuleCommand("CODE", null, code, null, null, null);
    }

    private PolicyRuleCommand composite(String type, PolicyRuleCommand... children) {
        return new PolicyRuleCommand(type, null, null, null, null, List.of(children));
    }

    private PolicyRuleCommand zoneRule(String type, List<String> zoneIds, PolicyRuleCommand... children) {
        return new PolicyRuleCommand(type, null, null, null, zoneIds, List.of(children));
    }

    // PP-05: Support age-based purchase restrictions.
    @Test
    void toPolicy_minAge_shouldValidateBuyerAge() {
        IPolicy policy = assembler.toPolicy(ruleWithValue("MIN_AGE", 18));

        assertThat(policy.validate(context(1, age(18)))).isTrue();
        assertThat(policy.validate(context(1, age(25)))).isTrue();
        assertThat(policy.validate(context(1, age(17)))).isFalse();
    }

    // PP-06 / DP-06: Support minimum ticket quantity restrictions.
    @Test
    void toPolicy_minTickets_shouldValidateMinimumTicketCount() {
        IPolicy policy = assembler.toPolicy(ruleWithValue("MIN_TICKETS", 2));

        assertThat(policy.validate(context(2, age(25)))).isTrue();
        assertThat(policy.validate(context(3, age(25)))).isTrue();
        assertThat(policy.validate(context(1, age(25)))).isFalse();
    }

    // PP-06 / DP-06: Support maximum ticket quantity restrictions.
    @Test
    void toPolicy_maxTickets_shouldValidateMaximumTicketCount() {
        IPolicy policy = assembler.toPolicy(ruleWithValue("MAX_TICKETS", 4));

        assertThat(policy.validate(context(1, age(25)))).isTrue();
        assertThat(policy.validate(context(4, age(25)))).isTrue();
        assertThat(policy.validate(context(5, age(25)))).isFalse();
    }

    // DP-06 / TST-09: Support time-range discount conditions.
    @Test
    void toPolicy_beforeDate_shouldValidateOnlyUntilDate() {
        IPolicy policy = assembler.toPolicy(ruleWithDate("BEFORE_DATE", LocalDate.now().plusDays(1)));

        assertThat(policy.validate(context(1, age(25)))).isTrue();
    }

    // DP-06 / TST-09: Support time-range discount conditions.
    @Test
    void toPolicy_beforeDate_whenDateAlreadyPassed_shouldReject() {
        IPolicy policy = assembler.toPolicy(ruleWithDate("BEFORE_DATE", LocalDate.now().minusDays(1)));

        assertThat(policy.validate(context(1, age(25)))).isFalse();
    }

    // DP-06 / TST-09: Support time-range discount conditions.
    @Test
    void toPolicy_afterDate_shouldValidateOnlyAfterDate() {
        IPolicy policy = assembler.toPolicy(ruleWithDate("AFTER_DATE", LocalDate.now().minusDays(1)));

        assertThat(policy.validate(context(1, age(25)))).isTrue();
    }

    // DP-06 / TST-09: Support time-range discount conditions.
    @Test
    void toPolicy_afterDate_whenDateIsInFuture_shouldReject() {
        IPolicy policy = assembler.toPolicy(ruleWithDate("AFTER_DATE", LocalDate.now().plusDays(1)));

        assertThat(policy.validate(context(1, age(25)))).isFalse();
    }

    // DP-07 / DP-08 / TST-08: Coupon discount applies only with the supplied valid code.
    @Test
    void toPolicy_code_shouldValidateDiscountCode() {
        IPolicy policy = assembler.toPolicy(ruleWithCode("STUDENT15"));

        assertThat(policy.validate(context(List.of(REGULAR_ZONE), age(25), "STUDENT15"))).isTrue();
        assertThat(policy.validate(context(List.of(REGULAR_ZONE), age(25), "WRONG"))).isFalse();
        assertThat(policy.validate(context(List.of(REGULAR_ZONE), age(25), null))).isFalse();
    }

    // PP-07 / PP-08: Support logical AND composition and arbitrarily deep composition.
    @Test
    void toPolicy_and_shouldRequireAllChildrenToPass() {
        IPolicy policy = assembler.toPolicy(
                composite(
                        "AND",
                        ruleWithValue("MIN_AGE", 18),
                        ruleWithValue("MAX_TICKETS", 4)
                )
        );

        assertThat(policy.validate(context(4, age(20)))).isTrue();
        assertThat(policy.validate(context(5, age(20)))).isFalse();
        assertThat(policy.validate(context(4, age(17)))).isFalse();
    }

    // PP-07 / PP-08: Support logical OR composition and arbitrarily deep composition.
    @Test
    void toPolicy_or_shouldRequireAtLeastOneChildToPass() {
        IPolicy policy = assembler.toPolicy(
                composite(
                        "OR",
                        ruleWithValue("MIN_AGE", 18),
                        ruleWithValue("MAX_TICKETS", 2)
                )
        );

        assertThat(policy.validate(context(5, age(20)))).isTrue();
        assertThat(policy.validate(context(2, age(17)))).isTrue();
        assertThat(policy.validate(context(5, age(17)))).isFalse();
    }

    // PP-08: Composition can be arbitrarily deep.
    @Test
    void toPolicy_nestedComposite_shouldValidateDeepPolicyTree() {
        IPolicy policy = assembler.toPolicy(
                composite(
                        "AND",
                        ruleWithValue("MIN_AGE", 18),
                        composite(
                                "OR",
                                ruleWithValue("MAX_TICKETS", 2),
                                ruleWithCode("VIP")
                        )
                )
        );

        assertThat(policy.validate(context(List.of(REGULAR_ZONE, REGULAR_ZONE), age(20), null))).isTrue();
        assertThat(policy.validate(context(List.of(REGULAR_ZONE, REGULAR_ZONE, REGULAR_ZONE), age(20), "VIP"))).isTrue();
        assertThat(policy.validate(context(List.of(REGULAR_ZONE, REGULAR_ZONE, REGULAR_ZONE), age(20), "WRONG"))).isFalse();
        assertThat(policy.validate(context(List.of(REGULAR_ZONE), age(17), "VIP"))).isFalse();
    }

    // PP-10 / DP-15: Support default no-restriction policy construction.
    @Test
    void toPolicy_allowAll_shouldAlwaysPass() {
        IPolicy policy = assembler.toPolicy(rule("ALLOW_ALL"));

        assertThat(policy.validate(context(1, age(10)))).isTrue();
        assertThat(policy.validate(context(10, age(10)))).isTrue();
    }

    // PP-10 / DP-15: Support explicit blocking policy construction.
    @Test
    void toPolicy_neverAllow_shouldAlwaysReject() {
        IPolicy policy = assembler.toPolicy(rule("NEVER_ALLOW"));

        assertThat(policy.validate(context(1, age(25)))).isFalse();
    }

    // PP-07 / PP-08: Zone-scoped composition should apply child policy only to affected tickets.
    @Test
    void toPolicy_zoneRestrict_shouldValidateOnlyAffectedZoneTickets() {
        IPolicy policy = assembler.toPolicy(
                zoneRule(
                        "ZONE_RESTRICT",
                        List.of(VIP_ZONE.value()),
                        ruleWithValue("MAX_TICKETS", 1)
                )
        );

        assertThat(policy.validate(context(List.of(VIP_ZONE), age(25), null))).isTrue();
        assertThat(policy.validate(context(List.of(VIP_ZONE, VIP_ZONE), age(25), null))).isFalse();

        // REGULAR tickets are not affected; ZONE_RESTRICT passes when no affected tickets exist.
        assertThat(policy.validate(context(List.of(REGULAR_ZONE, REGULAR_ZONE), age(25), null))).isTrue();
    }

    // PP-07 / PP-08: Zone requirement should fail if no required-zone ticket exists.
    @Test
    void toPolicy_zoneRequire_shouldRejectWhenNoAffectedZoneTicketExists() {
        IPolicy policy = assembler.toPolicy(
                zoneRule(
                        "ZONE_REQUIRE",
                        List.of(VIP_ZONE.value()),
                        ruleWithValue("MIN_TICKETS", 1)
                )
        );

        assertThat(policy.validate(context(List.of(VIP_ZONE), age(25), null))).isTrue();

        // ZONE_REQUIRE uses passWhenNoAffectedTickets=false.
        assertThat(policy.validate(context(List.of(REGULAR_ZONE), age(25), null))).isFalse();
    }

    // PP-03 / DP-03: Policy scope can be production-company-wide.
    @Test
    void toScope_companyWideWithNullEvents_shouldCreateCompanyWideScope() {
        PolicyScope scope = assembler.toScope(new PolicyScopeCommand(true, null));

        assertThat(scope.isCompanyWide()).isTrue();
        assertThat(scope.eventIds()).isEmpty();
        assertThat(scope.appliesTo(EVENT_ID)).isTrue();
    }

    // PP-03 / DP-03: Policy scope can be event-specific.
    @Test
    void toScope_eventSpecific_shouldConvertStringIdsToEventIds() {
        PolicyScope scope = assembler.toScope(new PolicyScopeCommand(false, Set.of("event-1", "event-2")));

        assertThat(scope.isCompanyWide()).isFalse();
        assertThat(scope.eventIds()).containsExactlyInAnyOrder(new EventId("event-1"), new EventId("event-2"));
        assertThat(scope.appliesTo(new EventId("event-1"))).isTrue();
        assertThat(scope.appliesTo(new EventId("event-3"))).isFalse();
    }

    // DP-04: Simple discount can have no extra predicate beyond scope/activation.
    @Test
    void toDiscount_whenRuleIsNull_shouldCreateAlwaysApplicableDiscount() {
        Discount discount = assembler.toDiscount(
                new DiscountCommand("Visible 20", BigDecimal.valueOf(20), null)
        );

        assertThat(discount.getDiscountName()).isEqualTo("Visible 20");
        assertThat(discount.getDiscountPercent()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(discount.validateDiscount(context(1, age(25)))).isTrue();
    }

    // DP-05 / DP-07: Conditional discount includes discount plus predicate.
    @Test
    void toDiscount_whenRuleExists_shouldCreateConditionalDiscount() {
        Discount discount = assembler.toDiscount(
                new DiscountCommand(
                        "Coupon 15",
                        BigDecimal.valueOf(15),
                        ruleWithCode("SAVE15")
                )
        );

        assertThat(discount.validateDiscount(context(List.of(REGULAR_ZONE), age(25), "SAVE15"))).isTrue();
        assertThat(discount.validateDiscount(context(List.of(REGULAR_ZONE), age(25), "WRONG"))).isFalse();
    }

    @Test
    void toPolicy_whenCommandIsNull_shouldThrow() {
        assertThatThrownBy(() -> assembler.toPolicy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("policy rule command");
    }

    @Test
    void toPolicy_whenTypeIsNull_shouldThrow() {
        assertThatThrownBy(() -> assembler.toPolicy(rule(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policy rule type");
    }

    @Test
    void toPolicy_whenTypeIsBlank_shouldThrow() {
        assertThatThrownBy(() -> assembler.toPolicy(rule("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policy rule type");
    }

    @Test
    void toPolicy_whenTypeUnsupported_shouldThrow() {
        assertThatThrownBy(() -> assembler.toPolicy(rule("UNKNOWN_POLICY")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported policy rule type");
    }

    // PP-09: Prevent invalid/empty composites.
    @Test
    void toPolicy_andWithoutChildren_shouldThrow() {
        assertThatThrownBy(() -> assembler.toPolicy(composite("AND")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AND policy requires children");
    }

    // PP-09: Prevent invalid/empty composites.
    @Test
    void toPolicy_orWithNullChildren_shouldThrow() {
        PolicyRuleCommand command = new PolicyRuleCommand("OR", null, null, null, null, null);

        assertThatThrownBy(() -> assembler.toPolicy(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OR policy requires children");
    }

    @Test
    void toPolicy_valueRuleWithoutValue_shouldThrow() {
        assertThatThrownBy(() -> assembler.toPolicy(rule("MAX_TICKETS")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MAX_TICKETS policy requires value");

        assertThatThrownBy(() -> assembler.toPolicy(rule("MIN_TICKETS")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MIN_TICKETS policy requires value");

        assertThatThrownBy(() -> assembler.toPolicy(rule("MIN_AGE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MIN_AGE policy requires value");
    }

    @Test
    void toPolicy_dateRuleWithoutDate_shouldThrow() {
        assertThatThrownBy(() -> assembler.toPolicy(rule("BEFORE_DATE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BEFORE_DATE policy requires date");

        assertThatThrownBy(() -> assembler.toPolicy(rule("AFTER_DATE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AFTER_DATE policy requires date");
    }

    @Test
    void toPolicy_codeRuleWithoutCode_shouldThrow() {
        assertThatThrownBy(() -> assembler.toPolicy(rule("CODE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CODE policy requires code");
    }

    @Test
    void toPolicy_zoneRuleWithoutZoneIds_shouldThrow() {
        assertThatThrownBy(() ->
                assembler.toPolicy(zoneRule("ZONE_RESTRICT", List.of(), ruleWithValue("MAX_TICKETS", 1)))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ZONE_RESTRICT policy requires zoneIds");

        assertThatThrownBy(() ->
                assembler.toPolicy(zoneRule("ZONE_REQUIRE", null, ruleWithValue("MAX_TICKETS", 1)))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ZONE_REQUIRE policy requires zoneIds");
    }

    @Test
    void toScope_whenCommandIsNull_shouldThrow() {
        assertThatThrownBy(() -> assembler.toScope(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("policy scope command");
    }

    @Test
    void toDiscount_whenCommandIsNull_shouldThrow() {
        assertThatThrownBy(() -> assembler.toDiscount(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("discount command");
    }

    @Test
    void toDiscount_whenPercentIsInvalid_shouldThrowDomainException() {
        assertThatThrownBy(() ->
                assembler.toDiscount(new DiscountCommand("Bad discount", BigDecimal.ZERO, null))
        )
                .isInstanceOf(DiscountPolicyException.class)
                .hasMessageContaining("Discount percent");
    }

    @Test
    void toDiscount_whenNameIsInvalid_shouldThrowDomainException() {
        assertThatThrownBy(() ->
                assembler.toDiscount(new DiscountCommand("   ", BigDecimal.TEN, null))
        )
                .isInstanceOf(DiscountPolicyException.class)
                .hasMessageContaining("Discount Name");
    }

        // PP-09 / TST-06:
    // Assembler should reject internally conflicting purchase-policy command trees.
    @Test
    void toPolicy_whenAndHasMinTicketsGreaterThanMaxTickets_shouldThrow() {
        PolicyRuleCommand command = composite(
                "AND",
                ruleWithValue("MIN_TICKETS", 5),
                ruleWithValue("MAX_TICKETS", 4)
        );

        assertThatThrownBy(() -> assembler.toPolicy(command))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("Invalid purchase policy")
                .hasMessageContaining("minimum tickets 5");
    }

    // DP-06 / PP-09 / TST-09:
    // Assembler should reject impossible date-window policy commands.
    @Test
    void toPolicy_whenAndHasImpossibleDateWindow_shouldThrow() {
        PolicyRuleCommand command = composite(
                "AND",
                ruleWithDate("AFTER_DATE", LocalDate.of(2026, 6, 10)),
                ruleWithDate("BEFORE_DATE", LocalDate.of(2026, 6, 1))
        );

        assertThatThrownBy(() -> assembler.toPolicy(command))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("Invalid purchase policy")
                .hasMessageContaining("impossible date window");
    }

    // PP-07 / PP-08 / PP-09:
    // OR is valid if at least one branch can pass.
    @Test
    void toPolicy_whenOrHasOneValidBranch_shouldNotThrow() {
        PolicyRuleCommand invalidBranch = composite(
                "AND",
                ruleWithValue("MIN_TICKETS", 5),
                ruleWithValue("MAX_TICKETS", 4)
        );

        PolicyRuleCommand command = composite(
                "OR",
                invalidBranch,
                ruleWithValue("MIN_AGE", 18)
        );

        IPolicy policy = assembler.toPolicy(command);

        assertThat(policy).isNotNull();
    }
}