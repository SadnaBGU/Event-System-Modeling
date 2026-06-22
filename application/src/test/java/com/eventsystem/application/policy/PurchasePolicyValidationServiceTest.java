package com.eventsystem.application.policy;

import com.eventsystem.application.TestPurchaseContexts;
import com.eventsystem.application.appexceptions.OrderViolatesPolicyException;
import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.member.IMemberInformationPort;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.policy.purchase.IPurchasePolicyRepository;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchasePolicyValidationServiceTest {

        @Mock
        private IPurchasePolicyRepository purchasePolicyRepository;

        @Mock
        private IEventManagementPort eventManagementPort;

        @Mock
        private IMemberInformationPort memberInformationPort;

        private PurchasePolicyValidationService service;

        private static final CompanyId COMPANY_ID = new CompanyId("company-1");
        private static final EventId EVENT_ID = new EventId("event-1");
        private static final ZoneId REGULAR_ZONE = new ZoneId("regular-zone");
        private static final MemberId MEMBER_ID = new MemberId("member-1");

        @BeforeEach
        void setUp() {
                service = new PurchasePolicyValidationService(
                                purchasePolicyRepository,
                                eventManagementPort,
                                memberInformationPort);
                lenient().when(eventManagementPort.companyOfEvent(EVENT_ID)).thenReturn(COMPANY_ID);
        }

        private PurchaseContext contextWithTickets(int ticketCount) {
                ZoneId[] zones = java.util.stream.IntStream.range(0, ticketCount)
                                .mapToObj(i -> REGULAR_ZONE)
                                .toArray(ZoneId[]::new);

                return TestPurchaseContexts.contextWithZones(
                                EVENT_ID,
                                COMPANY_ID,
                                LocalDate.now().minusYears(25),
                                null,
                                zones);
        }

        private PurchasePolicy maxTicketPolicy(int maxTickets) {
                PurchasePolicy policy = PurchasePolicy.newMaxTicketPolicy(
                                COMPANY_ID,
                                "Max " + maxTickets + " tickets",
                                maxTickets);
                policy.activateForEvent(EVENT_ID);
                return policy;
        }

        @Test
        void constructorRejectsNullDependencies() {
                assertThatThrownBy(() -> new PurchasePolicyValidationService(null, eventManagementPort,
                                memberInformationPort))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("purchasePolicyRepository");

                assertThatThrownBy(() -> new PurchasePolicyValidationService(purchasePolicyRepository, null,
                                memberInformationPort))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("eventOwnershipChecker");

                assertThatThrownBy(() -> new PurchasePolicyValidationService(purchasePolicyRepository,
                                eventManagementPort, null))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("memberInfoPort");
        }

        @Test
        void findById_returnsRepositoryResult() {
                PurchasePolicy policy = maxTicketPolicy(4);
                when(purchasePolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                Optional<PurchasePolicy> result = service.findById(policy.id());

                assertThat(result).contains(policy);
        }

        @Test
        void getByIdOrThrow_whenMissing_shouldThrowPolicyException() {
                PurchasePolicyId policyId = PurchasePolicyId.random();
                when(purchasePolicyRepository.findById(policyId)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.getByIdOrThrow(policyId))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("Purchase policy not found");
        }

        // UC9 / UAT-26:
        // Checkout succeeds when no applicable purchase policy rejects the order.
        @Test
        void evaluatePurchasePolicyFor_whenNoPolicies_shouldReturnSuccess_UAT26() {
                PurchaseContext context = contextWithTickets(2);

                when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of());

                PolicyValidationResult result = service.evaluatePurchasePolicyFor(context);

                assertThat(result.isSuccess()).isTrue();
        }

        // UC9 / UAT-27:
        // Checkout fails when order quantity exceeds purchase policy max-ticket rule.
        @Test
        void evaluatePurchasePolicyFor_whenMaxTicketPolicyFails_shouldReturnFailure_UAT27() {
                PurchaseContext context = contextWithTickets(5);
                PurchasePolicy policy = maxTicketPolicy(4);

                when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of(policy));

                PolicyValidationResult result = service.evaluatePurchasePolicyFor(context);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason())
                                .contains("Purchase policy")
                                .contains("Max 4 tickets");
        }

        // UC9 / UAT-27:
        // Checkout should throw application exception when purchase policy is violated.
        @Test
        void requirePurchasePolicyFor_whenPolicyFails_shouldThrowOrderViolatesPolicyException_UAT27() {
                PurchaseContext context = contextWithTickets(5);
                PurchasePolicy policy = maxTicketPolicy(4);

                when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of(policy));

                assertThatThrownBy(() -> service.requirePurchasePolicyFor(context))
                                .isInstanceOf(OrderViolatesPolicyException.class)
                                .hasMessageContaining("Purchase policy");
        }

        @Test
        void validatePurchasePolicyFor_shouldReturnBooleanResult() {
                PurchaseContext context = contextWithTickets(2);
                PurchasePolicy policy = maxTicketPolicy(4);

                when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of(policy));

                assertThat(service.validatePurchasePolicyFor(context)).isTrue();
        }

        @Test
        void createPurchaseContext_shouldBuildFullContextFromEventAndMemberPorts() {
                BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, MEMBER_ID.value());
                List<OrderItem> items = List.of(
                                new OrderItem(REGULAR_ZONE.value(), null, 2, Money.of(BigDecimal.TEN, "ILS")));

                when(eventManagementPort.companyOfEvent(EVENT_ID)).thenReturn(COMPANY_ID);
                when(memberInformationPort.getMemberBirthdate(MEMBER_ID)).thenReturn(LocalDate.of(2000, 1, 1));

                PurchaseContext context = service.createPurchaseContext(EVENT_ID, buyer, items);

                assertThat(context.eventId()).isEqualTo(EVENT_ID);
                assertThat(context.companyId()).isEqualTo(COMPANY_ID);
                assertThat(context.zonesOfEachEventTicket()).containsExactly(REGULAR_ZONE, REGULAR_ZONE);
                assertThat(context.buyerBirthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
                assertThat(context.discountCode()).isNull();
                assertThat(context.ticketCount()).isEqualTo(2);
                assertThat(context.ticketCountInZone(REGULAR_ZONE)).isEqualTo(2);
                assertThat(context.subtotalForZone(REGULAR_ZONE).amount()).isEqualByComparingTo("20");
                assertThat(context.baseTotal().amount()).isEqualByComparingTo("20");
        }

        @Test
        void getByIdOrThrow_whenPresent_shouldReturnPolicy() {
                PurchasePolicy policy = maxTicketPolicy(4);

                when(purchasePolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                assertThat(service.getByIdOrThrow(policy.id())).isSameAs(policy);
        }

        @Test
        void findByCompanyId_shouldDelegateToRepository() {
                PurchasePolicy policy = maxTicketPolicy(4);

                when(purchasePolicyRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(policy));

                assertThat(service.findByCompanyId(COMPANY_ID)).containsExactly(policy);
        }

        @Test
        void findActiveByCompanyId_shouldDelegateToRepository() {
                PurchasePolicy policy = maxTicketPolicy(4);

                when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID)).thenReturn(List.of(policy));

                assertThat(service.findActiveByCompanyId(COMPANY_ID)).containsExactly(policy);
        }

        @Test
        void findApplicableToEvent_shouldDelegateToRepository() {
                PurchasePolicy policy = maxTicketPolicy(4);

                when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of(policy));

                assertThat(service.findApplicableToEvent(EVENT_ID)).containsExactly(policy);
        }

        @Test
        void findApplicableToPurchase_shouldDelegateToRepository() {
                PurchasePolicy policy = maxTicketPolicy(4);

                when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of(policy));

                assertThat(service.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).containsExactly(policy);
        }

        @Test
        void existsById_shouldReturnRepositoryAnswer() {
                PurchasePolicyId id = PurchasePolicyId.random();

                when(purchasePolicyRepository.existsById(id)).thenReturn(true);

                assertThat(service.existsById(id)).isTrue();
        }

        @Test
        void requirePurchasePolicyFor_whenPolicyPasses_shouldNotThrow() {
                PurchaseContext context = contextWithTickets(2);
                PurchasePolicy policy = maxTicketPolicy(4);

                when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of(policy));

                assertThatCode(() -> service.requirePurchasePolicyFor(context))
                                .doesNotThrowAnyException();
        }

        @Test
        void evaluatePurchasePolicyFor_whenFirstPolicyPassesAndSecondFails_shouldReturnSecondFailure() {
                PurchaseContext context = contextWithTickets(5);

                PurchasePolicy pass = maxTicketPolicy(10);
                PurchasePolicy fail = maxTicketPolicy(4);

                when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                                .thenReturn(List.of(pass, fail));

                PolicyValidationResult result = service.evaluatePurchasePolicyFor(context);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("Max 4 tickets");
        }
}