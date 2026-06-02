package com.eventsystem.application.policy;

import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.member.IMemberInformationPort;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.policy.Discount;
import com.eventsystem.domain.policy.DiscountPolicy;
import com.eventsystem.domain.policy.DiscountPolicyId;
import com.eventsystem.domain.policy.DiscountSummary;
import com.eventsystem.domain.policy.PolicyScope;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.policy.basic.AlwaysTruePolicy;
import com.eventsystem.domain.policy.basic.CodePolicy;
import com.eventsystem.domain.policy.basic.MinTicketPolicy;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscountApplicationServiceTest {

        @Mock
        private IDiscountPolicyRepository discountPolicyRepository;

        @Mock
        private IEventManagementPort eventManagementPort;

        @Mock
        private IMemberInformationPort memberInformationPort;

        private DiscountApplicationService service;

        private static final CompanyId COMPANY_ID = new CompanyId("company-1");
        private static final EventId EVENT_ID = new EventId("event-1");
        private static final EventId OTHER_EVENT_ID = new EventId("event-2");
        private static final ZoneId REGULAR_ZONE = new ZoneId("regular-zone");
        private static final MemberId MEMBER_ID = new MemberId("member-1");

        @BeforeEach
        void setUp() {
                service = new DiscountApplicationService(
                                discountPolicyRepository,
                                eventManagementPort,
                                memberInformationPort);
        }

        private PurchaseContext contextWithTickets(int ticketCount) {
                List<ZoneId> zones = java.util.stream.IntStream.range(0, ticketCount)
                                .mapToObj(i -> REGULAR_ZONE)
                                .toList();

                return new PurchaseContext(
                                EVENT_ID,
                                COMPANY_ID,
                                zones,
                                LocalDate.now().minusYears(25),
                                null);
        }

        private DiscountPolicy activePolicyForEvent(EventId eventId, Discount discount) {
                DiscountPolicy policy = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                PolicyScope.forSingleEvent(eventId));
                policy.addDiscount(discount);
                policy.activate();
                return policy;
        }

        @Test
        void constructorRejectsNullDependencies() {
                assertThatThrownBy(
                                () -> new DiscountApplicationService(null, eventManagementPort, memberInformationPort))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("discountPolicyRepository");

                assertThatThrownBy(() -> new DiscountApplicationService(discountPolicyRepository, null,
                                memberInformationPort))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("eventOwnershipChecker");

                assertThatThrownBy(() -> new DiscountApplicationService(discountPolicyRepository, eventManagementPort,
                                null))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("memberInfoPort");
        }

        @Test
        void findById_returnsRepositoryResult() {
                DiscountPolicy policy = activePolicyForEvent(
                                EVENT_ID,
                                new Discount("Visible 10", BigDecimal.TEN, AlwaysTruePolicy.INSTANCE));

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                Optional<DiscountPolicy> result = service.findById(policy.id());

                assertThat(result).contains(policy);
        }

        @Test
        void getByIdOrThrow_whenMissing_shouldThrowPolicyException() {
                DiscountPolicyId policyId = DiscountPolicyId.random();
                when(discountPolicyRepository.findById(policyId)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.getByIdOrThrow(policyId))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("Discount policy not found");
        }

        // UC9 / DP-10:
        // System discovers active/applicable discounts for a purchase.
        @Test
        void doesDiscountApplyFor_whenApplicablePolicyMatches_shouldReturnTrue() {
                PurchaseContext context = contextWithTickets(2);

                DiscountPolicy policy = activePolicyForEvent(
                                EVENT_ID,
                                new Discount("Buy two discount", BigDecimal.valueOf(20), new MinTicketPolicy(2)));

                when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of(policy));

                assertThat(service.doesDiscountApplyFor(context)).isTrue();
        }

        // UC9 / DP-10:
        // No applicable discount should return false.
        @Test
        void doesDiscountApplyFor_whenNoPolicyMatches_shouldReturnFalse() {
                PurchaseContext context = contextWithTickets(1);

                DiscountPolicy policy = activePolicyForEvent(
                                EVENT_ID,
                                new Discount("Buy two discount", BigDecimal.valueOf(20), new MinTicketPolicy(2)));

                when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of(policy));

                assertThat(service.doesDiscountApplyFor(context)).isFalse();
        }

        // UC9 / DP-09 / DP-11 / DP-12:
        // Checkout calculates discount summary from applicable policies.
        @Test
        void calculateDiscountSummary_whenPolicyApplies_shouldReturnPositiveDiscount() {
                PurchaseContext context = contextWithTickets(2);
                Money baseTotal = Money.of(BigDecimal.valueOf(100), "ILS");

                DiscountPolicy policy = activePolicyForEvent(
                                EVENT_ID,
                                new Discount("Visible 20", BigDecimal.valueOf(20), AlwaysTruePolicy.INSTANCE));

                when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of(policy));

                DiscountSummary summary = service.calculateDiscountSummary(context, baseTotal);

                assertThat(summary.totalDiscount()).isGreaterThan(BigDecimal.ZERO);
                assertThat(summary.appliedDiscountsNames()).contains("Visible 20");
        }

        // UC9:
        // No applicable discounts should produce a no-discount summary.
        @Test
        void calculateDiscountSummary_whenNoPolicies_shouldReturnNoDiscountSummary() {
                PurchaseContext context = contextWithTickets(2);
                Money baseTotal = Money.of(BigDecimal.valueOf(100), "ILS");

                when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of());

                DiscountSummary summary = service.calculateDiscountSummary(context, baseTotal);

                assertThat(summary.totalDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(summary.appliedDiscountsNames()).isEmpty();
        }

        // UC9:
        // Checkout stores immutable discount snapshot based on current calculation.
        @Test
        void generateDiscountSnapshot_shouldUseCalculatedSummary() {
                PurchaseContext context = contextWithTickets(2);
                Money baseTotal = Money.of(BigDecimal.valueOf(100), "ILS");

                DiscountPolicy policy = activePolicyForEvent(
                                EVENT_ID,
                                new Discount("Visible 20", BigDecimal.valueOf(20), AlwaysTruePolicy.INSTANCE));

                when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of(policy));

                DiscountSnapshot snapshot = service.generateDiscountSnapshot(context, baseTotal);

                assertThat(snapshot.discountName()).contains("Visible 20");
                assertThat(snapshot.discountAmount().amount()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        void getAllActiveDiscountEvents_shouldIncludeExplicitAndCompanyWideEvents() {
                DiscountPolicy eventPolicy = activePolicyForEvent(
                                EVENT_ID,
                                new Discount("Event discount", BigDecimal.TEN, AlwaysTruePolicy.INSTANCE));

                DiscountPolicy companyPolicy = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                PolicyScope.companyWideScope());
                companyPolicy.addDiscount(
                                new Discount("Company discount", BigDecimal.TEN, AlwaysTruePolicy.INSTANCE));
                companyPolicy.activate();

                when(discountPolicyRepository.findActive()).thenReturn(List.of(eventPolicy, companyPolicy));
                when(eventManagementPort.allEventsOfCompany(COMPANY_ID)).thenReturn(List.of(OTHER_EVENT_ID));

                Set<EventId> result = service.getAllActiveDiscountEvents();

                assertThat(result).containsExactlyInAnyOrder(EVENT_ID, OTHER_EVENT_ID);
        }

        @Test
        void createPurchaseContext_shouldBuildFullContextIncludingDiscountCode() {
                BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, MEMBER_ID.value());
                List<OrderItem> items = List.of(
                                new OrderItem(REGULAR_ZONE.value(), null, 1, Money.of(BigDecimal.TEN, "ILS")));

                when(eventManagementPort.companyOfEvent(EVENT_ID)).thenReturn(COMPANY_ID);
                when(eventManagementPort.getZonesOfTicketsForEvent(EVENT_ID, items)).thenReturn(List.of(REGULAR_ZONE));
                when(memberInformationPort.getMemberBirthdate(MEMBER_ID)).thenReturn(LocalDate.of(2000, 1, 1));

                PurchaseContext context = service.createPurchaseContext(EVENT_ID, buyer, items, "SAVE20");

                assertThat(context.eventId()).isEqualTo(EVENT_ID);
                assertThat(context.companyId()).isEqualTo(COMPANY_ID);
                assertThat(context.zonesOfEachEventTicket()).containsExactly(REGULAR_ZONE);
                assertThat(context.buyerBirthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
                assertThat(context.discountCode()).isEqualTo("SAVE20");
        }

        @Test
        void getByIdOrThrow_whenPresent_shouldReturnPolicy() {
                DiscountPolicy policy = activePolicyForEvent(
                                EVENT_ID,
                                new Discount("Visible 10", BigDecimal.TEN, AlwaysTruePolicy.INSTANCE));

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                assertThat(service.getByIdOrThrow(policy.id())).isSameAs(policy);
        }

        @Test
        void findByCompanyId_shouldDelegateToRepository() {
                DiscountPolicy policy = activePolicyForEvent(
                                EVENT_ID,
                                new Discount("Visible 10", BigDecimal.TEN, AlwaysTruePolicy.INSTANCE));

                when(discountPolicyRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(policy));

                assertThat(service.findByCompanyId(COMPANY_ID)).containsExactly(policy);
        }

        @Test
        void findActiveByCompanyId_shouldDelegateToRepository() {
                DiscountPolicy policy = activePolicyForEvent(
                                EVENT_ID,
                                new Discount("Visible 10", BigDecimal.TEN, AlwaysTruePolicy.INSTANCE));

                when(discountPolicyRepository.findActiveByCompanyId(COMPANY_ID)).thenReturn(List.of(policy));

                assertThat(service.findActiveByCompanyId(COMPANY_ID)).containsExactly(policy);
        }

        @Test
        void findApplicableToPurchase_byEventOnly_shouldDelegateToRepository() {
                DiscountPolicy policy = activePolicyForEvent(
                                EVENT_ID,
                                new Discount("Visible 10", BigDecimal.TEN, AlwaysTruePolicy.INSTANCE));

                when(discountPolicyRepository.findApplicableToEvent(EVENT_ID)).thenReturn(List.of(policy));

                assertThat(service.findApplicableToPurchase(EVENT_ID)).containsExactly(policy);
        }

        @Test
        void findApplicableToPurchase_byCompanyAndEvent_shouldDelegateToRepository() {
                DiscountPolicy policy = activePolicyForEvent(
                                EVENT_ID,
                                new Discount("Visible 10", BigDecimal.TEN, AlwaysTruePolicy.INSTANCE));

                when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of(policy));

                assertThat(service.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).containsExactly(policy);
        }

        @Test
        void existsById_shouldReturnRepositoryAnswer() {
                DiscountPolicyId id = DiscountPolicyId.random();

                when(discountPolicyRepository.existsById(id)).thenReturn(true);

                assertThat(service.existsById(id)).isTrue();
        }

        @Test
        void generateDiscountSnapshot_whenNoDiscountApplies_shouldUseNoDiscountName() {
                PurchaseContext context = contextWithTickets(1);
                Money baseTotal = Money.of(BigDecimal.valueOf(100), "ILS");

                when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of());

                DiscountSnapshot snapshot = service.generateDiscountSnapshot(context, baseTotal);

                assertThat(snapshot.discountName()).isEqualTo("No Discount");
                assertThat(snapshot.discountAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @Deprecated
        void applyDiscount_whenNoLegacyPolicies_shouldReturnNoDiscountSnapshot() {
                Money baseTotal = Money.of(BigDecimal.valueOf(100), "ILS");

                when(discountPolicyRepository.findApplicableToEvent(EVENT_ID)).thenReturn(List.of());

                DiscountSnapshot snapshot = service.applyDiscount(EVENT_ID.value(), null, baseTotal);

                assertThat(snapshot.discountName()).isEqualTo("No Discount");
                assertThat(snapshot.discountAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @Deprecated
        void applyDiscount_whenLegacyPolicyApplies_shouldReturnBestDiscountSnapshot() {
                Money baseTotal = Money.of(BigDecimal.valueOf(100), "ILS");

                DiscountPolicy tenPercent = activePolicyForEvent(
                                EVENT_ID,
                                Discount.GeneralDiscount("Ten", BigDecimal.TEN, null));

                DiscountPolicy twentyPercent = activePolicyForEvent(
                                EVENT_ID,
                                Discount.GeneralDiscount("Twenty", BigDecimal.valueOf(20), null));

                when(discountPolicyRepository.findApplicableToEvent(EVENT_ID))
                                .thenReturn(List.of(tenPercent, twentyPercent));

                DiscountSnapshot snapshot = service.applyDiscount(EVENT_ID.value(), null, baseTotal);

                assertThat(snapshot.discountName()).contains("Twenty");
                assertThat(snapshot.discountAmount().amount()).isEqualByComparingTo("20.0");
        }

        @Test
        @Deprecated
        void applyDiscount_whenLegacyCouponDoesNotMatch_shouldReturnNoDiscountSnapshot() {
                Money baseTotal = Money.of(BigDecimal.valueOf(100), "ILS");

                DiscountPolicy couponPolicy = activePolicyForEvent(
                                EVENT_ID,
                                new Discount("Secret coupon", BigDecimal.valueOf(30), new CodePolicy("SAVE30")));

                when(discountPolicyRepository.findApplicableToEvent(EVENT_ID))
                                .thenReturn(List.of(couponPolicy));

                DiscountSnapshot snapshot = service.applyDiscount(EVENT_ID.value(), "WRONG", baseTotal);

                assertThat(snapshot.discountName()).isEqualTo("No Discount");
                assertThat(snapshot.discountAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void calculateDiscountSummary_whenSeveralPoliciesApply_shouldChooseBestSummary() {
                PurchaseContext context = contextWithTickets(2);
                Money baseTotal = Money.of(BigDecimal.valueOf(100), "ILS");

                DiscountPolicy tenPercent = activePolicyForEvent(
                                EVENT_ID,
                                Discount.GeneralDiscount("Ten", BigDecimal.TEN, null));

                DiscountPolicy twentyPercent = activePolicyForEvent(
                                EVENT_ID,
                                Discount.GeneralDiscount("Twenty", BigDecimal.valueOf(20), null));

                when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of(tenPercent, twentyPercent));

                DiscountSummary summary = service.calculateDiscountSummary(context, baseTotal);

                assertThat(summary.appliedDiscountsNames()).contains("Twenty");
                assertThat(summary.totalDiscount()).isEqualByComparingTo("20.0");
        }

        // DP-14 / UAT-45:
        // Event-scoped active visible discount should expose that event id to higher
        // layers.
        @Test
        void getEventIdsWithActiveVisibleDiscounts_shouldReturnEventScopedVisibleDiscountEvents_UAT45_DP14() {
                DiscountPolicy eventPolicy = activePolicyForEvent(
                                EVENT_ID,
                                new Discount(
                                                "Visible event discount",
                                                BigDecimal.TEN,
                                                AlwaysTruePolicy.INSTANCE,
                                                true,
                                                LocalDate.now().plusDays(1)));

                when(discountPolicyRepository.findActiveWithVisibleDiscounts())
                                .thenReturn(List.of(eventPolicy));

                List<EventId> result = service.getEventIdsWithActiveVisibleDiscounts();

                assertThat(result).containsExactly(EVENT_ID);
        }

        // DP-03 / DP-14:
        // Company-wide active visible discount should expand to all company events.
        @Test
        void getEventIdsWithActiveVisibleDiscounts_whenCompanyWide_shouldReturnAllCompanyEvents_DP03_DP14() {
                DiscountPolicy companyPolicy = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                PolicyScope.companyWideScope());

                companyPolicy.addDiscount(new Discount(
                                "Visible company discount",
                                BigDecimal.TEN,
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().plusDays(1)));

                companyPolicy.activate();

                when(discountPolicyRepository.findActiveWithVisibleDiscounts())
                                .thenReturn(List.of(companyPolicy));
                when(eventManagementPort.allEventsOfCompany(COMPANY_ID))
                                .thenReturn(List.of(EVENT_ID, OTHER_EVENT_ID));

                List<EventId> result = service.getEventIdsWithActiveVisibleDiscounts();

                assertThat(result).containsExactlyInAnyOrder(EVENT_ID, OTHER_EVENT_ID);
        }

        // DP-03 / DP-14:
        // Duplicate event ids from event-scoped and company-wide visible discounts
        // should be returned once.
        @Test
        void getEventIdsWithActiveVisibleDiscounts_shouldReturnDistinctEventIds_DP14() {
                DiscountPolicy eventPolicy = activePolicyForEvent(
                                EVENT_ID,
                                new Discount(
                                                "Visible event discount",
                                                BigDecimal.TEN,
                                                AlwaysTruePolicy.INSTANCE,
                                                true,
                                                LocalDate.now().plusDays(1)));

                DiscountPolicy companyPolicy = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                PolicyScope.companyWideScope());

                companyPolicy.addDiscount(new Discount(
                                "Visible company discount",
                                BigDecimal.TEN,
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().plusDays(1)));

                companyPolicy.activate();

                when(discountPolicyRepository.findActiveWithVisibleDiscounts())
                                .thenReturn(List.of(eventPolicy, companyPolicy));
                when(eventManagementPort.allEventsOfCompany(COMPANY_ID))
                                .thenReturn(List.of(EVENT_ID, OTHER_EVENT_ID));

                List<EventId> result = service.getEventIdsWithActiveVisibleDiscounts();

                assertThat(result).containsExactlyInAnyOrder(EVENT_ID, OTHER_EVENT_ID);
        }
}