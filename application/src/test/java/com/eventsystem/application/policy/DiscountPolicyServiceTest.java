package com.eventsystem.application.policy;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.member.IMemberInformationPort;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;

import com.eventsystem.domain.policy.Discount;
import com.eventsystem.domain.policy.DiscountPolicy;
import com.eventsystem.domain.policy.DiscountPolicyId;
import com.eventsystem.domain.policy.PolicyScope;
import com.eventsystem.domain.policy.DiscountSummary;
import com.eventsystem.domain.policy.PurchaseContext;
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
    //private static final EventId OTHER_EVENT_ID = new EventId("event-2");

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
}