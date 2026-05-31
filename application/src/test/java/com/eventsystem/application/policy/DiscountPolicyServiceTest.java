package com.eventsystem.application.policy;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
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
import com.eventsystem.domain.policy.PolicyScope;
import com.eventsystem.domain.policy.DiscountSummary;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.policy.basic.CodePolicy;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscountPolicyServiceTest {

    @Mock
    private IDiscountPolicyRepository discountPolicyRepository;

    @Mock
    private ICompanyPermissionServicePort permissionChecker;

    @Mock
    private IEventManagementPort eventServicePort;

    @Mock
    private IMemberInformationPort memberInfoPort;


    private DiscountPolicyService service;

    private static final String ACTOR_ID_STR = "actor-1";
    private static final String COMPANY_ID_STR = "company-1";
    private static final String OTHER_COMPANY_ID_STR = "company-2";

    private static final MemberId ACTOR_ID = new MemberId(ACTOR_ID_STR);
    private static final CompanyId COMPANY_ID = new CompanyId(COMPANY_ID_STR);
    private static final CompanyId OTHER_COMPANY_ID = new CompanyId(OTHER_COMPANY_ID_STR);

    private static final EventId EVENT_ID = new EventId("event-1");
    private static final EventId OTHER_EVENT_ID = new EventId("event-2");
    private static final EventId FOREIGN_EVENT_ID = new EventId("foreign-event");



    private static final ZoneId REGULAR_ZONE = new ZoneId("regular-zone");

    @BeforeEach
    void setUp() {
        service = new DiscountPolicyService(discountPolicyRepository, permissionChecker, eventServicePort, memberInfoPort);
    }

    private DiscountPolicy companyWideWithDiscount() {
        DiscountPolicy p =  new DiscountPolicy(DiscountPolicyId.random(),COMPANY_ID,PolicyScope.companyWideScope());
        p.addDiscount(Discount.GeneralDiscount("TEST", BigDecimal.ONE));
        return p;
    }

    private DiscountPolicy companyWidePolicy() {
        return new DiscountPolicy(
                DiscountPolicyId.random(),
                COMPANY_ID,
                PolicyScope.companyWideScope()
        );
    }

    private DiscountPolicy eventScopedPolicy(EventId eventId) {
        return new DiscountPolicy(
                DiscountPolicyId.random(),
                COMPANY_ID,
                PolicyScope.forEvents(Set.of(eventId))
        );
    }

    private PurchaseContext purchaseContext() {
        return new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                List.of(REGULAR_ZONE),
                LocalDate.now().minusYears(25),
                null
        );
    }


    private Money baseCost100() {
        return Money.of(BigDecimal.valueOf(100), "ILS");
    }

    // UAT-45: Set Visible Discount
    @Test
    void saveDiscountPolicy_whenAuthorized_savesPolicy_UAT45() {
        DiscountPolicy policy = companyWidePolicy();

        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);

        service.saveDiscountPolicy(ACTOR_ID, COMPANY_ID, policy);

        verify(discountPolicyRepository).save(policy);
    }

    @Test
    void saveDiscountPolicy_whenUnauthorized_throwsAndDoesNotSave() {
        DiscountPolicy policy = companyWidePolicy();

        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.saveDiscountPolicy(ACTOR_ID, COMPANY_ID, policy))
                .isInstanceOf(SecurityException.class);

        verify(discountPolicyRepository, never()).save(any());
    }

    @Test
    void saveDiscountPolicy_whenCompanyArgumentDoesNotMatchPolicyCompany_shouldThrowAndNotSave() {
        DiscountPolicy policy = companyWideWithDiscount();
        policy.addDiscount(Discount.GeneralDiscount("Visible", BigDecimal.valueOf(20)));
        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, OTHER_COMPANY_ID)).thenReturn(true);
        policy.activate();
        assertThatThrownBy(() -> service.saveDiscountPolicy(ACTOR_ID, OTHER_COMPANY_ID, policy))
                .isInstanceOf(SecurityException.class);

        verify(discountPolicyRepository, never()).save(any());
    }

    @Test
    void findById_returnsRepositoryResult() {
        DiscountPolicy policy = companyWidePolicy();

        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

        Optional<DiscountPolicy> result = service.findById(policy.id());

        assertThat(result).contains(policy);
    }

    @Test
    void getByIdOrThrow_whenPolicyExists_returnsPolicy() {
        DiscountPolicy policy = companyWidePolicy();

        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

        DiscountPolicy result = service.getByIdOrThrow(policy.id());

        assertThat(result).isSameAs(policy);
    }

    @Test
    void getByIdOrThrow_whenPolicyMissing_throwsPolicyException() {
        DiscountPolicyId policyId = DiscountPolicyId.random();

        when(discountPolicyRepository.findById(policyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByIdOrThrow(policyId))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("Discount policy not found");
    }

    @Test
    void findByCompanyId_delegatesToRepository() {
        DiscountPolicy policy = companyWidePolicy();

        when(discountPolicyRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(policy));

        List<DiscountPolicy> result = service.findByCompanyId(COMPANY_ID);

        assertThat(result).containsExactly(policy);
    }

    @Test
    void findActiveByCompanyId_delegatesToRepository() {
        DiscountPolicy policy = companyWideWithDiscount();
        policy.activate();

        when(discountPolicyRepository.findActiveByCompanyId(COMPANY_ID)).thenReturn(List.of(policy));

        List<DiscountPolicy> result = service.findActiveByCompanyId(COMPANY_ID);

        assertThat(result).containsExactly(policy);
    }

    @Test
    void findApplicableToPurchase_delegatesToRepository() {
        DiscountPolicy policy = eventScopedPolicy(EVENT_ID);

        when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                .thenReturn(List.of(policy));

        List<DiscountPolicy> result = service.findApplicableToPurchase(COMPANY_ID, EVENT_ID);

        assertThat(result).containsExactly(policy);
    }

    // UAT-45: Set Visible Discount
    @Test
    void activateDiscountPolicy_whenAuthorizedAndCompanyOwnsPolicy_activatesAndSaves_UAT45() {
        DiscountPolicy policy = companyWideWithDiscount();

        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

        service.activateDiscountPolicy(ACTOR_ID, COMPANY_ID, policy.id());

        assertThat(policy.isActive()).isTrue();
        verify(discountPolicyRepository).save(policy);
    }

    @Test
    void activateDiscountPolicy_whenUnauthorized_throwsAndDoesNotSave() {
        DiscountPolicy policy = companyWidePolicy();

        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.activateDiscountPolicy(ACTOR_ID, COMPANY_ID, policy.id()))
                .isInstanceOf(SecurityException.class);

        verify(discountPolicyRepository, never()).save(any());
    }

    @Test
    void activateDiscountPolicy_whenCompanyDoesNotOwnPolicy_throwsAndDoesNotSave() {
        DiscountPolicy policy = companyWidePolicy();

        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, OTHER_COMPANY_ID)).thenReturn(true);
        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> service.activateDiscountPolicy(ACTOR_ID, OTHER_COMPANY_ID, policy.id()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("other");

        verify(discountPolicyRepository, never()).save(any());
    }

    @Test
    void deactivateDiscountPolicy_whenAuthorizedAndCompanyOwnsPolicy_deactivatesAndSaves() {
        DiscountPolicy policy = companyWideWithDiscount();
        policy.activate();

        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

        service.deactivateDiscountPolicy(ACTOR_ID, COMPANY_ID, policy.id());

        assertThat(policy.isActive()).isFalse();
        verify(discountPolicyRepository).save(policy);
    }

    // UAT-46: Set Coupon Code / add discount to policy
    @Test
    void addDiscountToPolicy_whenAuthorizedAndCompanyOwnsPolicy_addsDiscountAndSaves_UAT46() {
        DiscountPolicy policy = companyWidePolicy();
        Discount discount = Discount.GeneralDiscount("Visible", BigDecimal.valueOf(20));

        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

        service.addDiscountToPolicy(ACTOR_ID, COMPANY_ID, policy.id(), discount);

        assertThat(policy.discounts()).contains(discount);
        verify(discountPolicyRepository).save(policy);
    }

    @Test
    void addDiscountToPolicy_whenCompanyDoesNotOwnPolicy_throwsAndDoesNotSave() {
        DiscountPolicy policy = companyWidePolicy();
        Discount discount = Discount.GeneralDiscount("Visible", BigDecimal.valueOf(20));

        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, OTHER_COMPANY_ID)).thenReturn(true);
        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

        assertThatThrownBy(() ->
                service.addDiscountToPolicy(ACTOR_ID, OTHER_COMPANY_ID, policy.id(), discount)
        ).isInstanceOf(SecurityException.class);

        verify(discountPolicyRepository, never()).save(any());
    }

    @Test
    void deleteDiscountPolicy_whenAuthorizedAndCompanyOwnsPolicy_deletesPolicy() {
        DiscountPolicy policy = companyWidePolicy();

        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

        service.deleteDiscountPolicy(ACTOR_ID, COMPANY_ID, policy.id());

        verify(discountPolicyRepository).deleteById(policy.id());
    }

    @Test
    void deleteDiscountPolicy_whenCompanyDoesNotOwnPolicy_throwsAndDoesNotDelete() {
        DiscountPolicy policy = companyWidePolicy();

        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, OTHER_COMPANY_ID)).thenReturn(true);
        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> service.deleteDiscountPolicy(ACTOR_ID, OTHER_COMPANY_ID, policy.id()))
                .isInstanceOf(SecurityException.class);

        verify(discountPolicyRepository, never()).deleteById(any());
    }

    @Test
    void existsById_delegatesToRepository() {
        DiscountPolicyId policyId = DiscountPolicyId.random();

        when(discountPolicyRepository.existsById(policyId)).thenReturn(true);

        assertThat(service.existsById(policyId)).isTrue();
    }

    // UAT-47: Invalid coupon / no applicable discount
    @Test
    void calculateDiscountSummary_whenNoApplicablePolicies_returnsNoDiscountSummary_UAT47() {
        when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                .thenReturn(List.of());

        DiscountSummary summary = service.calculateDiscountSummary(purchaseContext(), baseCost100());

        assertThat(summary.appliedDiscountsNames()).isEmpty();
        assertThat(summary.totalDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // UAT-45: Visible discount at checkout
    @Test
    void calculateDiscountSummary_whenOneApplicablePolicy_returnsThatPolicySummary_UAT45() {
        DiscountPolicy policy = companyWideWithDiscount();
        policy.activate();
        policy.addDiscount(Discount.GeneralDiscount("Visible", BigDecimal.valueOf(20)));

        when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                .thenReturn(List.of(policy));

        DiscountSummary summary = service.calculateDiscountSummary(purchaseContext(), baseCost100());

        assertThat(summary.appliedDiscountsNames()).containsExactly("Visible");
        assertThat(summary.totalDiscount()).isEqualByComparingTo("20");
    }

    @Test
    void calculateDiscountSummary_whenMultiplePoliciesApply_choosesBestSummary() {
        DiscountPolicy smallPolicy = companyWideWithDiscount();
        smallPolicy.addDiscount(Discount.GeneralDiscount("Small", BigDecimal.valueOf(10)));

        DiscountPolicy largePolicy = eventScopedPolicy(EVENT_ID);
        largePolicy.addDiscount(Discount.GeneralDiscount("Large", BigDecimal.valueOf(25)));
        smallPolicy.activate();
        largePolicy.activate();

        when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                .thenReturn(List.of(smallPolicy, largePolicy));

        DiscountSummary summary = service.calculateDiscountSummary(purchaseContext(), baseCost100());

        assertThat(summary.appliedDiscountsNames()).containsExactly("Large");
        assertThat(summary.totalDiscount()).isEqualByComparingTo("25");
    }

    // UAT-26: Successful checkout - discount snapshot creation
    @Test
    void generateDiscountSnapshot_usesBestApplicableSummary_UAT26() {
        DiscountPolicy policy = companyWidePolicy();
        policy.addDiscount(Discount.GeneralDiscount("Visible", BigDecimal.valueOf(20)));
        policy.activate();
        when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                .thenReturn(List.of(policy));

        DiscountSnapshot snapshot = service.generateDiscountSnapshot(purchaseContext(), baseCost100());

        assertThat(snapshot.discountName()).contains("Visible");
        assertThat(snapshot.discountAmount().amount()).isEqualByComparingTo("20");
        assertThat(snapshot.discountAmount().currency()).isEqualTo("ILS");
    }

    @Test
    void fromPurchaseInfo_withoutDiscountCode_usesProvidedBirthDateAndNullCode() {
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        PurchaseContext context = PurchaseContext.fromPurchaseInfo(
                EVENT_ID,
                COMPANY_ID,
                List.of(REGULAR_ZONE),
                birthDate
        );

        assertThat(context.eventId()).isEqualTo(EVENT_ID);
        assertThat(context.companyId()).isEqualTo(COMPANY_ID);
        assertThat(context.zonesOfEachEventTicket()).containsExactly(REGULAR_ZONE);
        assertThat(context.buyerBirthDate()).isEqualTo(birthDate);
        assertThat(context.discountCode()).isNull();
    }

    @Test
    void fromPurchaseInfo_withDiscountCode_trimsCodeAndUsesProvidedBirthDate() {
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        PurchaseContext context = PurchaseContext.fromPurchaseInfo(
                EVENT_ID,
                COMPANY_ID,
                List.of(REGULAR_ZONE),
                birthDate,
                "  SAVE20  "
        );

        assertThat(context.buyerBirthDate()).isEqualTo(birthDate);
        assertThat(context.discountCode()).isEqualTo("SAVE20");
    }

    @Test
    void fromPurchaseInfo_withBlankDiscountCode_setsCodeToNull() {
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        PurchaseContext context = PurchaseContext.fromPurchaseInfo(
                EVENT_ID,
                COMPANY_ID,
                List.of(REGULAR_ZONE),
                birthDate,
                "   "
        );

        assertThat(context.discountCode()).isNull();
    }

    @Test
    void constructorRejectsNullDependencies() {
        assertThatThrownBy(() -> new DiscountPolicyService(null, permissionChecker, eventServicePort, memberInfoPort))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new DiscountPolicyService(discountPolicyRepository, null, eventServicePort, memberInfoPort))
                .isInstanceOf(NullPointerException.class);
                
        assertThatThrownBy(() -> new DiscountPolicyService(discountPolicyRepository, permissionChecker, null, memberInfoPort))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new DiscountPolicyService(discountPolicyRepository, permissionChecker, eventServicePort, null))
                .isInstanceOf(NullPointerException.class);
    }

    
    // UAT-45 / TST-07: define visible discount and manage scope.
    @Test
    void modifyDiscountPolicyScope_whenNewEventBelongsToCompany_changesScopeAndSaves_UAT45() {
        DiscountPolicy policy = activeCompanyWidePolicy("Visible", BigDecimal.TEN);
        PolicyScope newScope = PolicyScope.forEvents(Set.of(EVENT_ID, OTHER_EVENT_ID));

        allowManageDiscounts();
        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);
        when(eventServicePort.isEventByCompany(OTHER_EVENT_ID, COMPANY_ID)).thenReturn(true);

        service.modifyDiscountPolicyScope(ACTOR_ID, COMPANY_ID, policy.id(), newScope);

        assertThat(policy.scope()).isEqualTo(newScope);
        verify(discountPolicyRepository).save(policy);
    }

    @Test
    void modifyDiscountPolicyScope_whenEventDoesNotBelongToCompany_throwsAndDoesNotSave() {
        DiscountPolicy policy = activeCompanyWidePolicy("Visible", BigDecimal.TEN);
        PolicyScope newScope = PolicyScope.forSingleEvent(FOREIGN_EVENT_ID);

        allowManageDiscounts();
        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));
        when(eventServicePort.isEventByCompany(FOREIGN_EVENT_ID, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.modifyDiscountPolicyScope(ACTOR_ID, COMPANY_ID, policy.id(), newScope))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("actor is not allowed");

        verify(discountPolicyRepository, never()).save(any());
    }

    @Test
    void setToCompanyWide_whenAuthorized_setsCompanyWideAndSaves() {
        DiscountPolicy policy = activeEventPolicy(EVENT_ID, "Visible", BigDecimal.TEN);

        allowManageDiscounts();
        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

        service.setToCompanyWide(ACTOR_ID, COMPANY_ID, policy.id());

        assertThat(policy.scope().isCompanyWide()).isTrue();
        assertThat(policy.scope().eventIds()).containsExactly(EVENT_ID);
        verify(discountPolicyRepository).save(policy);
    }

    @Test
    void setToNotCompanyWide_whenNoEventScope_deactivatesPolicyAndSaves() {
        DiscountPolicy policy = activeCompanyWidePolicy("Visible", BigDecimal.TEN);

        allowManageDiscounts();
        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

        service.setToNotCompanyWide(ACTOR_ID, COMPANY_ID, policy.id());

        assertThat(policy.scope().isCompanyWide()).isFalse();
        assertThat(policy.isActive()).isFalse();
        verify(discountPolicyRepository).save(policy);
    }

    @Test
    void addEventToPolicy_whenEventBelongsToCompany_addsEventAndSaves() {
        DiscountPolicy policy = activeCompanyWidePolicy("Visible", BigDecimal.TEN);

        allowManageDiscounts();
        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);

        service.addEventToPolicy(ACTOR_ID, COMPANY_ID, policy.id(), EVENT_ID);

        assertThat(policy.scope().eventIds()).contains(EVENT_ID);
        verify(discountPolicyRepository).save(policy);
    }

    @Test
    void removeEventFromPolicy_whenLastEventAndNotCompanyWide_deactivatesPolicyAndSaves() {
        DiscountPolicy policy = activeEventPolicy(EVENT_ID, "Visible", BigDecimal.TEN);

        allowManageDiscounts();
        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);

        service.removeEventFromPolicy(ACTOR_ID, COMPANY_ID, policy.id(), EVENT_ID);

        assertThat(policy.scope().eventIds()).doesNotContain(EVENT_ID);
        assertThat(policy.isActive()).isFalse();
        verify(discountPolicyRepository).save(policy);
    }

    @Test
    void clearAllDiscountsOfCompany_deletesOnlyPoliciesReturnedForCompany() {
        DiscountPolicy first = activeEventPolicy(EVENT_ID, "First", BigDecimal.TEN);
        DiscountPolicy second = activeEventPolicy(OTHER_EVENT_ID, "Second", BigDecimal.valueOf(15));

        allowManageDiscounts();
        when(discountPolicyRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(first, second));

        service.clearAllDiscountsOfCompany(ACTOR_ID, COMPANY_ID);

        verify(discountPolicyRepository).deleteById(first.id());
        verify(discountPolicyRepository).deleteById(second.id());
    }

    @Test
    void deactivateAllCompanyDiscounts_deactivatesOnlyActivePoliciesAndSavesThem() {
        DiscountPolicy active = activeEventPolicy(EVENT_ID, "Active", BigDecimal.TEN);
        DiscountPolicy inactive = eventPolicy(EVENT_ID, "Inactive", BigDecimal.TEN);

        allowManageDiscounts();
        when(discountPolicyRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(active, inactive));

        service.deactivateAllCompanyDiscounts(ACTOR_ID, COMPANY_ID);

        assertThat(active.isActive()).isFalse();
        assertThat(inactive.isActive()).isFalse();
        verify(discountPolicyRepository).save(active);
        verify(discountPolicyRepository, never()).save(inactive);
    }

    @Test
    void removeEventFromAllDiscountScopes_removesOnlyMatchingEventScopes() {
        DiscountPolicy matching = activeEventPolicy(EVENT_ID, "Matching", BigDecimal.TEN);
        DiscountPolicy other = activeEventPolicy(OTHER_EVENT_ID, "Other", BigDecimal.TEN);

        allowManageDiscounts();
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);
        when(discountPolicyRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(matching, other));

        service.removeEventFromAllDiscountScopes(ACTOR_ID, COMPANY_ID, EVENT_ID);

        assertThat(matching.scope().eventIds()).doesNotContain(EVENT_ID);
        assertThat(matching.isActive()).isFalse();
        assertThat(other.scope().eventIds()).containsExactly(OTHER_EVENT_ID);
        verify(discountPolicyRepository).save(matching);
        verify(discountPolicyRepository, never()).save(other);
    }

    @Test
    void clearEventsFromAllDiscounts_preservesCompanyWideButRemovesExplicitEvents() {
        DiscountPolicy policy = activeEventPolicy(EVENT_ID, "Visible", BigDecimal.TEN);
        policy.setCompanyWide();

        allowManageDiscounts();
        when(discountPolicyRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(policy));

        service.clearEventsFromAllDiscounts(ACTOR_ID, COMPANY_ID);

        assertThat(policy.scope().isCompanyWide()).isTrue();
        assertThat(policy.scope().eventIds()).isEmpty();
        assertThat(policy.isActive()).isTrue();
        verify(discountPolicyRepository).save(policy);
    }

    // UAT-46 / UAT-47 / TST-08: hidden coupon applies only with valid code.
    @Test
    void doesDiscountApplyFor_whenCouponCodeIsValid_returnsTrue_UAT46() {
        DiscountPolicy policy = activeEventPolicy(EVENT_ID, "SAVE20", BigDecimal.valueOf(20), new CodePolicy("SAVE20"));

        when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of(policy));

        boolean result = service.doesDiscountApplyFor(contextWithCode("SAVE20"));

        assertThat(result).isTrue();
    }

    @Test
    void doesDiscountApplyFor_whenCouponCodeIsInvalid_returnsFalse_UAT47() {
        DiscountPolicy policy = activeEventPolicy(EVENT_ID, "SAVE20", BigDecimal.valueOf(20), new CodePolicy("SAVE20"));

        when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of(policy));

        boolean result = service.doesDiscountApplyFor(contextWithCode("BAD-CODE"));

        assertThat(result).isFalse();
    }

    @Test
    void calculateDiscountSummary_whenBestOfSeveralPolicies_returnsLargestDiscount() {
        DiscountPolicy ten = activeEventPolicy(EVENT_ID, "Ten", BigDecimal.TEN);
        DiscountPolicy thirty = activeEventPolicy(EVENT_ID, "Thirty", BigDecimal.valueOf(30));

        when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of(ten, thirty));

        DiscountSummary summary = service.calculateDiscountSummary(contextWithCode(null), baseCost100());

        assertThat(summary.appliedDiscountsNames()).containsExactly("Thirty");
        assertThat(summary.totalDiscount()).isEqualByComparingTo("30");
    }

    @Test
    void generateDiscountSnapshot_whenNoDiscount_usesNoDiscountNameAndZeroAmount() {
        when(discountPolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of());

        DiscountSnapshot snapshot = service.generateDiscountSnapshot(contextWithCode(null), baseCost100());

        assertThat(snapshot.discountName()).isEqualTo("No Discount");
        assertThat(snapshot.discountAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.discountAmount().currency()).isEqualTo("ILS");
    }

    @Test
    @SuppressWarnings("deprecation")
    void applyDiscount_legacyMethod_whenNoApplicablePolicy_returnsNoDiscountSnapshot() {
        when(discountPolicyRepository.findApplicableToEvent(EVENT_ID)).thenReturn(List.of());

        DiscountSnapshot snapshot = service.applyDiscount(EVENT_ID.value(), null, baseCost100());

        assertThat(snapshot.discountName()).isEqualTo("No Discount");
        assertThat(snapshot.discountAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @SuppressWarnings("deprecation")
    void applyDiscount_legacyMethod_whenCouponApplies_returnsBestDiscountSnapshot_UAT46() {
        DiscountPolicy coupon = activeEventPolicy(EVENT_ID, "SAVE20", BigDecimal.valueOf(20), new CodePolicy("SAVE20"));

        when(discountPolicyRepository.findApplicableToEvent(EVENT_ID)).thenReturn(List.of(coupon));

        DiscountSnapshot snapshot = service.applyDiscount(EVENT_ID.value(), "SAVE20", baseCost100());

        assertThat(snapshot.discountName()).isEqualTo("SAVE20");
        assertThat(snapshot.discountAmount().amount()).isEqualByComparingTo("20");
    }

    @Test
    void getAllActiveDiscountEvents_includesEventScopesAndCompanyWideEventsWithoutDuplicates() {
        DiscountPolicy eventPolicy = activeEventPolicy(EVENT_ID, "Event", BigDecimal.TEN);
        DiscountPolicy companyWidePolicy = activeCompanyWidePolicy("Company", BigDecimal.valueOf(15));

        when(discountPolicyRepository.findActive()).thenReturn(List.of(eventPolicy, companyWidePolicy));
        when(eventServicePort.allEventsOfCompany(COMPANY_ID)).thenReturn(List.of(EVENT_ID, OTHER_EVENT_ID));

        Set<EventId> result = service.getAllActiveDiscountEvents();

        assertThat(result).containsExactlyInAnyOrder(EVENT_ID, OTHER_EVENT_ID);
    }

    @Test
    void createPurchaseContext_buildsContextUsingEventZonesAndBuyerBirthdate() {
        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, "member-1");
        List<OrderItem> items = List.of(new OrderItem(REGULAR_ZONE.value(), null, 1, baseCost100()));
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(eventServicePort.companyOfEvent(EVENT_ID)).thenReturn(COMPANY_ID);
        when(eventServicePort.getZonesOfTicketsForEvent(EVENT_ID, items)).thenReturn(List.of(REGULAR_ZONE));
        when(memberInfoPort.getMemberBirthdate(new MemberId("member-1"))).thenReturn(birthDate);

        PurchaseContext context = service.createPurchaseContext(EVENT_ID, buyer, items, " SAVE20 ");

        assertThat(context.eventId()).isEqualTo(EVENT_ID);
        assertThat(context.companyId()).isEqualTo(COMPANY_ID);
        assertThat(context.zonesOfEachEventTicket()).containsExactly(REGULAR_ZONE);
        assertThat(context.buyerBirthDate()).isEqualTo(birthDate);
        assertThat(context.discountCode()).isEqualTo("SAVE20");
    }

    private void allowManageDiscounts() {
        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
    }

    private static DiscountPolicy eventPolicy(EventId eventId, String discountName, BigDecimal percent) {
        DiscountPolicy policy = DiscountPolicy.inactiveForSingleEvent(COMPANY_ID, eventId);
        policy.addDiscount(Discount.GeneralDiscount(discountName, percent));
        return policy;
    }

    private static DiscountPolicy activeEventPolicy(EventId eventId, String discountName, BigDecimal percent) {
        DiscountPolicy policy = eventPolicy(eventId, discountName, percent);
        policy.activate();
        return policy;
    }

    private static DiscountPolicy activeEventPolicy(EventId eventId,
                                                    String discountName,
                                                    BigDecimal percent,
                                                    com.eventsystem.domain.policy.IPolicy condition) {
        DiscountPolicy policy = DiscountPolicy.inactiveForSingleEvent(COMPANY_ID, eventId);
        policy.addDiscount(new Discount(discountName, percent, condition));
        policy.activate();
        return policy;
    }

    private static DiscountPolicy activeCompanyWidePolicy(String discountName, BigDecimal percent) {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.addDiscount(Discount.GeneralDiscount(discountName, percent));
        policy.activate();
        return policy;
    }

    private static PurchaseContext contextWithCode(String code) {
        return new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                List.of(REGULAR_ZONE),
                LocalDate.now().minusYears(25),
                code
        );
    }

}