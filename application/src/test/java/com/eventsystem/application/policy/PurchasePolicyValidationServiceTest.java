package com.eventsystem.application.policy;

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
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.policy.PurchasePolicy;
import com.eventsystem.domain.policy.PurchasePolicyId;
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
                memberInformationPort
        );
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
                null
        );
    }

    private PurchasePolicy maxTicketPolicy(int maxTickets) {
        PurchasePolicy policy = PurchasePolicy.NewMaxTicketPolicy(
                COMPANY_ID,
                "Max " + maxTickets + " tickets",
                maxTickets
        );
        policy.activateForEvent(EVENT_ID);
        return policy;
    }

    @Test
    void constructorRejectsNullDependencies() {
        assertThatThrownBy(() ->
                new PurchasePolicyValidationService(null, eventManagementPort, memberInformationPort)
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("purchasePolicyRepository");

        assertThatThrownBy(() ->
                new PurchasePolicyValidationService(purchasePolicyRepository, null, memberInformationPort)
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventOwnershipChecker");

        assertThatThrownBy(() ->
                new PurchasePolicyValidationService(purchasePolicyRepository, eventManagementPort, null)
        )
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
                new OrderItem(REGULAR_ZONE.value(), null, 2, Money.of(BigDecimal.TEN, "ILS"))
        );

        when(eventManagementPort.companyOfEvent(EVENT_ID)).thenReturn(COMPANY_ID);
        when(eventManagementPort.getZonesOfTicketsForEvent(EVENT_ID, items)).thenReturn(List.of(REGULAR_ZONE, REGULAR_ZONE));
        when(memberInformationPort.getMemberBirthdate(MEMBER_ID)).thenReturn(LocalDate.of(2000, 1, 1));

        PurchaseContext context = service.createPurchaseContext(EVENT_ID, buyer, items);

        assertThat(context.eventId()).isEqualTo(EVENT_ID);
        assertThat(context.companyId()).isEqualTo(COMPANY_ID);
        assertThat(context.zonesOfEachEventTicket()).containsExactly(REGULAR_ZONE, REGULAR_ZONE);
        assertThat(context.buyerBirthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(context.discountCode()).isNull();
    }

    @Test
    @Deprecated
    void validatePurchasePolicy_legacyMethod_shouldAllowOnlyWhenNoApplicablePolicies() {
        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, MEMBER_ID.value());
        List<OrderItem> items = List.of();

        when(purchasePolicyRepository.findApplicableToEvent(EVENT_ID)).thenReturn(List.of());

        assertThat(service.validatePurchasePolicy(EVENT_ID.value(), buyer, items)).isTrue();

        when(purchasePolicyRepository.findApplicableToEvent(EVENT_ID)).thenReturn(List.of(maxTicketPolicy(4)));

        assertThat(service.validatePurchasePolicy(EVENT_ID.value(), buyer, items)).isFalse();
    }
}