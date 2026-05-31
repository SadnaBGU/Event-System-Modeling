package com.eventsystem.application.policy;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.member.IMemberInformationPort;
import com.eventsystem.application.policy.policybuilder.DiscountCommand;
import com.eventsystem.application.policy.policybuilder.DiscountPolicyCommand;
import com.eventsystem.application.policy.policybuilder.PolicyCommandAssembler;
import com.eventsystem.application.policy.policybuilder.PolicyRuleCommand;
import com.eventsystem.application.policy.policybuilder.PolicyScopeCommand;
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
import org.mockito.ArgumentCaptor;
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

    private PolicyCommandAssembler policyAssembler;
    private DiscountPolicyService service;

    private static final String ACTOR_ID_STR = "actor-1";
    private static final String COMPANY_ID_STR = "company-1";
    private static final String OTHER_COMPANY_ID_STR = "company-2";

    private static final MemberId ACTOR_ID = new MemberId(ACTOR_ID_STR);
    private static final CompanyId COMPANY_ID = new CompanyId(COMPANY_ID_STR);
    private static final CompanyId OTHER_COMPANY_ID = new CompanyId(OTHER_COMPANY_ID_STR);

    private static final EventId EVENT_ID = new EventId("event-1");
    private static final EventId OTHER_EVENT_ID = new EventId("event-2");

    private static final ZoneId REGULAR_ZONE = new ZoneId("regular-zone");

    @BeforeEach
    void setUp() {
        policyAssembler = new PolicyCommandAssembler();
        service = new DiscountPolicyService(discountPolicyRepository, permissionChecker, eventServicePort, memberInfoPort, policyAssembler);
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
        assertThatThrownBy(() -> new DiscountPolicyService(null, permissionChecker, eventServicePort, memberInfoPort, policyAssembler))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new DiscountPolicyService(discountPolicyRepository, null, eventServicePort, memberInfoPort, policyAssembler))
                .isInstanceOf(NullPointerException.class);
                
        assertThatThrownBy(() -> new DiscountPolicyService(discountPolicyRepository, permissionChecker, null, memberInfoPort, policyAssembler))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new DiscountPolicyService(discountPolicyRepository, permissionChecker, eventServicePort, null, policyAssembler))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new DiscountPolicyService(discountPolicyRepository, permissionChecker, eventServicePort, memberInfoPort, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Command-based discount policy creation for UI/API
    // ─────────────────────────────────────────────────────────────────────

    // PRD-03 / DP-03 / DP-05 / DP-06 / DP-11 / TST-07 / TST-09:
    // Owner/manager defines active event-scoped discount policy through application layer.
    // Discount is conditional on max ticket quantity.
    @Test
    void createDiscountPolicy_fromCommand_shouldAssemblePolicyAndSaveIt() {
        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);

        DiscountPolicyCommand command = new DiscountPolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Early bird max 4 tickets",
                new PolicyScopeCommand(false, Set.of(EVENT_ID.value())),
                List.of(
                        new DiscountCommand(
                                "EARLY_BIRD_20",
                                BigDecimal.valueOf(20),
                                new PolicyRuleCommand("MAX_TICKETS", 4, null, null, null, null)
                        )
                ),
                false,
                true
        );

        DiscountPolicyId policyId = service.createDiscountPolicy(command);

        ArgumentCaptor<DiscountPolicy> captor = ArgumentCaptor.forClass(DiscountPolicy.class);
        verify(discountPolicyRepository).save(captor.capture());

        DiscountPolicy savedPolicy = captor.getValue();

        assertThat(policyId).isEqualTo(savedPolicy.id());
        assertThat(savedPolicy.companyId()).isEqualTo(COMPANY_ID);
        assertThat(savedPolicy.scope().isCompanyWide()).isFalse();
        assertThat(savedPolicy.scope().eventIds()).containsExactly(EVENT_ID);
        assertThat(savedPolicy.isActive()).isTrue();
        assertThat(savedPolicy.isStackable()).isFalse();
        assertThat(savedPolicy.discounts()).hasSize(1);
        assertThat(savedPolicy.discounts().get(0).getDiscountName()).isEqualTo("EARLY_BIRD_20");
        assertThat(savedPolicy.discounts().get(0).getDiscountPercent()).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    // DP-03 / DP-04 / DP-12 / TST-07:
    // Owner/manager defines company-wide stackable visible discount.
    @Test
    void createDiscountPolicy_companyWideStackableSimpleDiscount_shouldSaveStackableActivePolicy() {
        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);

        DiscountPolicyCommand command = new DiscountPolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Company visible discount",
                new PolicyScopeCommand(true, Set.of()),
                List.of(
                        new DiscountCommand("VISIBLE_10", BigDecimal.TEN, null),
                        new DiscountCommand("VISIBLE_5", BigDecimal.valueOf(5), null)
                ),
                true,
                true
        );

        service.createDiscountPolicy(command);

        ArgumentCaptor<DiscountPolicy> captor = ArgumentCaptor.forClass(DiscountPolicy.class);
        verify(discountPolicyRepository).save(captor.capture());

        DiscountPolicy savedPolicy = captor.getValue();

        assertThat(savedPolicy.companyId()).isEqualTo(COMPANY_ID);
        assertThat(savedPolicy.scope().isCompanyWide()).isTrue();
        assertThat(savedPolicy.isActive()).isTrue();
        assertThat(savedPolicy.isStackable()).isTrue();
        assertThat(savedPolicy.discounts()).hasSize(2);

        verify(eventServicePort, never()).isEventByCompany(any(), any());
    }

    // DP-07 / DP-08 / TST-08:
    // Coupon discount is created as hidden/code-based discount.
    @Test
    void createDiscountPolicy_couponCommand_shouldCreateCouponBasedDiscount() {
        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);

        DiscountPolicyCommand command = new DiscountPolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Student coupon",
                new PolicyScopeCommand(false, Set.of(EVENT_ID.value())),
                List.of(
                        new DiscountCommand(
                                "STUDENT_15",
                                BigDecimal.valueOf(15),
                                new PolicyRuleCommand("CODE", null, "STUDENT15", null, null, null)
                        )
                ),
                false,
                true
        );

        service.createDiscountPolicy(command);

        ArgumentCaptor<DiscountPolicy> captor = ArgumentCaptor.forClass(DiscountPolicy.class);
        verify(discountPolicyRepository).save(captor.capture());

        DiscountPolicy savedPolicy = captor.getValue();

        PurchaseContext correctCodeContext = new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                List.of(REGULAR_ZONE),
                LocalDate.now().minusYears(25),
                "STUDENT15"
        );

        PurchaseContext wrongCodeContext = new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                List.of(REGULAR_ZONE),
                LocalDate.now().minusYears(25),
                "WRONG"
        );

        assertThat(savedPolicy.isPurchaseEligibleForDiscount(correctCodeContext)).isTrue();
        assertThat(savedPolicy.isPurchaseEligibleForDiscount(wrongCodeContext)).isFalse();
    }

    // DP-06 / TST-09:
    // Time-range discount condition is accepted from command.
    @Test
    void createDiscountPolicy_timeRangeCommand_shouldCreateTimeBasedDiscount() {
        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);

        DiscountPolicyCommand command = new DiscountPolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Before date discount",
                new PolicyScopeCommand(false, Set.of(EVENT_ID.value())),
                List.of(
                        new DiscountCommand(
                                "EARLY_25",
                                BigDecimal.valueOf(25),
                                new PolicyRuleCommand(
                                        "BEFORE_DATE",
                                        null,
                                        null,
                                        LocalDate.now().plusDays(1).toString(),
                                        null,
                                        null
                                )
                        )
                ),
                false,
                true
        );

        service.createDiscountPolicy(command);

        ArgumentCaptor<DiscountPolicy> captor = ArgumentCaptor.forClass(DiscountPolicy.class);
        verify(discountPolicyRepository).save(captor.capture());

        DiscountPolicy savedPolicy = captor.getValue();

        assertThat(savedPolicy.isPurchaseEligibleForDiscount(purchaseContext())).isTrue();
    }

    // TST-13:
    // Authorization failure is enforced in the Application layer, not only UI.
    @Test
    void createDiscountPolicy_whenUnauthorized_shouldThrowAndNotSave() {
        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);

        DiscountPolicyCommand command = new DiscountPolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Unauthorized discount",
                new PolicyScopeCommand(false, Set.of(EVENT_ID.value())),
                List.of(new DiscountCommand("VISIBLE_10", BigDecimal.TEN, null)),
                false,
                true
        );

        assertThatThrownBy(() -> service.createDiscountPolicy(command))
                .isInstanceOf(SecurityException.class);

        verify(discountPolicyRepository, never()).save(any());
        verify(eventServicePort, never()).isEventByCompany(any(), any());
    }

    // TST-13 / DP-03:
    // Application layer rejects event-scoped discount policy when event does not belong to company.
    @Test
    void createDiscountPolicy_whenEventDoesNotBelongToCompany_shouldThrowAndNotSave() {
        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
        when(eventServicePort.isEventByCompany(OTHER_EVENT_ID, COMPANY_ID)).thenReturn(false);

        DiscountPolicyCommand command = new DiscountPolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Foreign event discount",
                new PolicyScopeCommand(false, Set.of(OTHER_EVENT_ID.value())),
                List.of(new DiscountCommand("VISIBLE_10", BigDecimal.TEN, null)),
                false,
                true
        );

        assertThatThrownBy(() -> service.createDiscountPolicy(command))
                .isInstanceOf(SecurityException.class);

        verify(discountPolicyRepository, never()).save(any());
    }

    // TST-03 / TST-07:
    // Discount policy definition must contain at least one discount.
    @Test
    void createDiscountPolicy_whenDiscountListIsEmpty_shouldThrowAndNotSave() {
        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);

        DiscountPolicyCommand command = new DiscountPolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Empty discount policy",
                new PolicyScopeCommand(false, Set.of(EVENT_ID.value())),
                List.of(),
                false,
                true
        );

        assertThatThrownBy(() -> service.createDiscountPolicy(command))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("at least one discount");

        verify(discountPolicyRepository, never()).save(any());
    }

    // TST-03 / TST-07:
    // Discount policy definition must contain at least one discount.
    @Test
    void createDiscountPolicy_whenDiscountListIsNull_shouldThrowAndNotSave() {
        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);

        DiscountPolicyCommand command = new DiscountPolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Null discount policy",
                new PolicyScopeCommand(false, Set.of(EVENT_ID.value())),
                null,
                false,
                true
        );

        assertThatThrownBy(() -> service.createDiscountPolicy(command))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("at least one discount");

        verify(discountPolicyRepository, never()).save(any());
    }

    // TST-03:
    // Null command should be rejected.
    @Test
    void createDiscountPolicy_whenCommandIsNull_shouldThrow() {
        assertThatThrownBy(() -> service.createDiscountPolicy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("command");
    }

    // DP-05 / DP-07 / TST-07 / TST-08:
    // Add conditional discount through command-based application method.
    @Test
    void addDiscountToPolicy_fromCommand_shouldAssembleDiscountAndSavePolicy() {
        DiscountPolicy policy = companyWidePolicy();

        when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
        when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

        DiscountCommand command = new DiscountCommand(
                "COUPON_20",
                BigDecimal.valueOf(20),
                new PolicyRuleCommand("CODE", null, "SAVE20", null, null, null)
        );

        service.addDiscountToPolicy(ACTOR_ID, COMPANY_ID, policy.id(), command);

        assertThat(policy.discounts()).hasSize(1);
        assertThat(policy.discounts().get(0).getDiscountName()).isEqualTo("COUPON_20");
        assertThat(policy.discounts().get(0).getDiscountPercent()).isEqualByComparingTo(BigDecimal.valueOf(20));

        verify(discountPolicyRepository).save(policy);
    }

    // TST-03:
    // Null discount command should be rejected.
    @Test
    void addDiscountToPolicy_whenCommandIsNull_shouldThrow() {
        DiscountPolicy policy = companyWidePolicy();

        assertThatThrownBy(() ->
                service.addDiscountToPolicy(ACTOR_ID, COMPANY_ID, policy.id(), (DiscountCommand) null)
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("command");

        verify(discountPolicyRepository, never()).save(any());
    }
}