package com.eventsystem.application.policy;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.policy.policybuilder.DiscountCommand;
import com.eventsystem.application.policy.policybuilder.DiscountPolicyCommand;
import com.eventsystem.application.policy.policybuilder.PolicyCommandAssembler;
import com.eventsystem.application.policy.policybuilder.PurchasePolicyCommand;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.policy.PolicyConflictDetector;
import com.eventsystem.domain.policy.discount.Discount;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.domain.policy.rule.IPolicy;
import com.eventsystem.domain.policy.rule.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.rule.basic.MinAgePolicy;
import com.eventsystem.domain.policy.rule.basic.MinTicketPolicy;
import com.eventsystem.domain.policy.rule.composite.AndPolicy;
import com.eventsystem.domain.policy.shared.PolicyScope;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class PolicyManagementService implements IPolicyManagementPort {

    private static final Logger logger = LoggerFactory.getLogger(PolicyManagementService.class);

    private final IPurchasePolicyRepository purchasePolicyRepository;
    private final IDiscountPolicyRepository discountPolicyRepository;
    private final ICompanyPermissionServicePort permissionChecker;
    private final IEventManagementPort eventOwnershipChecker;
    private final PolicyCommandAssembler policyCommandAssembler;

    public PolicyManagementService(
            IPurchasePolicyRepository purchasePolicyRepository,
            IDiscountPolicyRepository discountPolicyRepository,
            ICompanyPermissionServicePort permissionChecker,
            IEventManagementPort eventOwnershipChecker,
            PolicyCommandAssembler policyCommandAssembler
    ) {
        this.purchasePolicyRepository = Objects.requireNonNull(
                purchasePolicyRepository,
                "purchasePolicyRepository must not be null"
        );
        this.discountPolicyRepository = Objects.requireNonNull(
                discountPolicyRepository,
                "discountPolicyRepository must not be null"
        );
        this.permissionChecker = Objects.requireNonNull(
                permissionChecker,
                "permissionChecker must not be null"
        );
        this.eventOwnershipChecker = Objects.requireNonNull(
                eventOwnershipChecker,
                "eventOwnershipChecker must not be null"
        );
        this.policyCommandAssembler = Objects.requireNonNull(
                policyCommandAssembler,
                "policyCommandAssembler must not be null"
        );
    }

    // ---------------------------------------------------------------------
    // Purchase policy creation / update
    // ---------------------------------------------------------------------

    public PurchasePolicyId createPurchasePolicy(PurchasePolicyCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        MemberId actorId = new MemberId(command.actorId());
        CompanyId companyId = new CompanyId(command.companyId());

        requireManagePurchasePoliciesPermission(actorId, companyId);

        PolicyScope scope = policyCommandAssembler.toScope(command.scope());
        requireCompanyOwnsScopeEvents(companyId, scope);

        IPolicy rule = policyCommandAssembler.toPolicy(command.rule());

        PurchasePolicy purchasePolicy = new PurchasePolicy(
                PurchasePolicyId.random(),
                companyId,
                command.policyName(),
                scope,
                rule
        );

        requirePurchasePolicyCompatibleWithActiveDiscounts(purchasePolicy);

        purchasePolicyRepository.save(purchasePolicy);

        logger.info(
                "Purchase policy created. policyId={}, companyId={}, active={}",
                purchasePolicy.id(),
                purchasePolicy.companyId(),
                purchasePolicy.isActive()
        );

        return purchasePolicy.id();
    }

    public void savePurchasePolicy(MemberId actorId, CompanyId companyId, PurchasePolicy purchasePolicy) {
        Objects.requireNonNull(purchasePolicy, "purchasePolicy must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);
        requirePurchasePolicyBelongsToCompany(purchasePolicy, companyId);
        requireCompanyOwnsScopeEvents(companyId, purchasePolicy.scope());

        requirePurchasePolicyCompatibleWithActiveDiscounts(purchasePolicy);

        purchasePolicyRepository.save(purchasePolicy);

        logger.info(
                "Purchase policy saved. policyId={}, companyId={}",
                purchasePolicy.id(),
                companyId
        );
    }

    public void modifyPurchasePolicyScope(
            MemberId actorId,
            CompanyId companyId,
            PurchasePolicyId policyId,
            PolicyScope newScope
    ) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(newScope, "newScope must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);

        PurchasePolicy policy = getPurchasePolicyOrThrow(policyId);
        requirePurchasePolicyBelongsToCompany(policy, companyId);
        requireCompanyOwnsScopeEvents(companyId, newScope);

        policy.setScopeTo(newScope);

        requirePurchasePolicyCompatibleWithActiveDiscounts(policy);

        purchasePolicyRepository.save(policy);

        logger.info(
                "Purchase policy scope modified. policyId={}, companyId={}, active={}",
                policy.id(),
                companyId,
                policy.isActive()
        );
    }

    public void setPurchasePolicyCompanyWide(
            MemberId actorId,
            CompanyId companyId,
            PurchasePolicyId policyId
    ) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);

        PurchasePolicy policy = getPurchasePolicyOrThrow(policyId);
        requirePurchasePolicyBelongsToCompany(policy, companyId);

        policy.setCompanyWide();

        requirePurchasePolicyCompatibleWithActiveDiscounts(policy);

        purchasePolicyRepository.save(policy);

        logger.info(
                "Purchase policy set to company-wide. policyId={}, companyId={}",
                policy.id(),
                companyId
        );
    }

    public void setPurchasePolicyNotCompanyWide(
            MemberId actorId,
            CompanyId companyId,
            PurchasePolicyId policyId
    ) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);

        PurchasePolicy policy = getPurchasePolicyOrThrow(policyId);
        requirePurchasePolicyBelongsToCompany(policy, companyId);

        policy.deactivateCompanyWide();

        purchasePolicyRepository.save(policy);

        logger.info(
                "Purchase policy set to not company-wide. policyId={}, companyId={}",
                policy.id(),
                companyId
        );
    }

    public void addEventToPurchasePolicy(
            MemberId actorId,
            CompanyId companyId,
            PurchasePolicyId policyId,
            EventId eventId
    ) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        PurchasePolicy policy = getPurchasePolicyOrThrow(policyId);
        requirePurchasePolicyBelongsToCompany(policy, companyId);

        policy.activateForEvent(eventId);

        requirePurchasePolicyCompatibleWithActiveDiscounts(policy);

        purchasePolicyRepository.save(policy);

        logger.info(
                "Event added to purchase policy. policyId={}, eventId={}",
                policy.id(),
                eventId
        );
    }

    public void removeEventFromPurchasePolicy(
            MemberId actorId,
            CompanyId companyId,
            PurchasePolicyId policyId,
            EventId eventId
    ) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        PurchasePolicy policy = getPurchasePolicyOrThrow(policyId);
        requirePurchasePolicyBelongsToCompany(policy, companyId);

        policy.deactivateForEvent(eventId);

        purchasePolicyRepository.save(policy);

        logger.info(
                "Event removed from purchase policy. policyId={}, eventId={}",
                policy.id(),
                eventId
        );
    }

    public void renamePurchasePolicy(
            MemberId actorId,
            CompanyId companyId,
            PurchasePolicyId policyId,
            String newName
    ) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(newName, "newName must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);

        PurchasePolicy policy = getPurchasePolicyOrThrow(policyId);
        requirePurchasePolicyBelongsToCompany(policy, companyId);

        policy.setNameTo(newName);

        purchasePolicyRepository.save(policy);

        logger.info(
                "Purchase policy renamed. policyId={}, newName={}",
                policy.id(),
                newName
        );
    }

    public void deletePurchasePolicy(MemberId actorId, CompanyId companyId, PurchasePolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);

        PurchasePolicy policy = getPurchasePolicyOrThrow(policyId);
        requirePurchasePolicyBelongsToCompany(policy, companyId);

        purchasePolicyRepository.deleteById(policyId);

        logger.info("Purchase policy deleted. policyId={}, companyId={}", policyId, companyId);
    }

    public void clearAllPurchasePoliciesOfCompany(MemberId actorId, CompanyId companyId) {
        requireManagePurchasePoliciesPermission(actorId, companyId);

        List<PurchasePolicy> companyPolicies = purchasePolicyRepository.findByCompanyId(companyId);

        for (PurchasePolicy policy : companyPolicies) {
            purchasePolicyRepository.deleteById(policy.id());
        }

        logger.info(
                "All purchase policies cleared for company. companyId={}, deletedCount={}",
                companyId,
                companyPolicies.size()
        );
    }

    public void deactivateAllCompanyPurchasePolicies(MemberId actorId, CompanyId companyId) {
        requireManagePurchasePoliciesPermission(actorId, companyId);

        List<PurchasePolicy> companyPolicies = purchasePolicyRepository.findByCompanyId(companyId);

        for (PurchasePolicy policy : companyPolicies) {
            if (policy.isActive()) {
                policy.setScopeTo(PolicyScope.clearScope());
                purchasePolicyRepository.save(policy);
            }
        }

        logger.info("All purchase policies deactivated for company. companyId={}", companyId);
    }

    public void removeEventFromAllPurchasePolicyScopes(
            MemberId actorId,
            CompanyId companyId,
            EventId eventId
    ) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        List<PurchasePolicy> companyPolicies = purchasePolicyRepository.findByCompanyId(companyId);

        for (PurchasePolicy policy : companyPolicies) {
            if (policy.scope().eventIds().contains(eventId)) {
                policy.deactivateForEvent(eventId);
                purchasePolicyRepository.save(policy);
            }
        }

        logger.info(
                "Event removed from all purchase policy scopes. companyId={}, eventId={}",
                companyId,
                eventId
        );
    }

    public void clearEventsFromAllPurchasePolicies(MemberId actorId, CompanyId companyId) {
        requireManagePurchasePoliciesPermission(actorId, companyId);

        List<PurchasePolicy> companyPolicies = purchasePolicyRepository.findByCompanyId(companyId);

        for (PurchasePolicy policy : companyPolicies) {
            policy.setScopeTo(new PolicyScope(policy.scope().isCompanyWide(), java.util.Set.of()));
            purchasePolicyRepository.save(policy);
        }

        logger.info("Explicit event scopes cleared from all purchase policies. companyId={}", companyId);
    }

    @Override
    public void createNewAllowAllPurchasePolicy(MemberId actorId, CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        PurchasePolicy policy = PurchasePolicy.allowAll(
                PurchasePolicyId.random(),
                companyId,
                "AllowAll"
        );
        policy.activateForEvent(eventId);

        requirePurchasePolicyCompatibleWithActiveDiscounts(policy);

        purchasePolicyRepository.save(policy);

        logger.info(
                "Allow-all purchase policy created. policyId={}, companyId={}, eventId={}",
                policy.id(),
                companyId,
                eventId
        );
    }

    @Override
    public void setNotAllowedPurchasePolicy(MemberId actorId, CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        PurchasePolicy policy = PurchasePolicy.notAllowed(
                PurchasePolicyId.random(),
                companyId,
                "NotAllowed"
        );
        policy.activateForEvent(eventId);

        requirePurchasePolicyCompatibleWithActiveDiscounts(policy);

        purchasePolicyRepository.save(policy);

        logger.info(
                "Never-allow purchase policy created. policyId={}, companyId={}, eventId={}",
                policy.id(),
                companyId,
                eventId
        );
    }

    @Override
    public boolean doesHaveActivePurchasePolicy(EventId eventId, CompanyId companyId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        return !purchasePolicyRepository.findActiveByCompanyId(companyId).isEmpty()
                || !purchasePolicyRepository.findApplicableToEvent(eventId).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Discount policy creation / update
    // ---------------------------------------------------------------------

    public DiscountPolicyId createDiscountPolicy(DiscountPolicyCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        MemberId actorId = new MemberId(command.actorId());
        CompanyId companyId = new CompanyId(command.companyId());

        requireManageDiscountPoliciesPermission(actorId, companyId);

        PolicyScope scope = policyCommandAssembler.toScope(command.scope());
        requireCompanyOwnsScopeEvents(companyId, scope);

        if (command.discounts() == null || command.discounts().isEmpty()) {
            throw new PolicyException("Discount policy must contain at least one discount");
        }

        DiscountPolicy discountPolicy = new DiscountPolicy(
                DiscountPolicyId.random(),
                companyId,
                scope
        );

        if (command.stackable()) {
            discountPolicy.allowStacking();
        } else {
            discountPolicy.disallowStacking();
        }

        for (DiscountCommand discountCommand : command.discounts()) {
            Discount discount = policyCommandAssembler.toDiscount(discountCommand);
            discountPolicy.addDiscount(discount);
        }

        if (command.activate()) {
            discountPolicy.activate();
        }

        requireDiscountPolicyCompatibleWithActivePurchasePolicies(discountPolicy);

        discountPolicyRepository.save(discountPolicy);

        logger.info(
                "Discount policy created. policyId={}, companyId={}, active={}, stackable={}, discountCount={}",
                discountPolicy.id(),
                discountPolicy.companyId(),
                discountPolicy.isActive(),
                discountPolicy.isStackable(),
                discountPolicy.discounts().size()
        );

        return discountPolicy.id();
    }

    public void saveDiscountPolicy(MemberId actorId, CompanyId companyId, DiscountPolicy discountPolicy) {
        Objects.requireNonNull(discountPolicy, "discountPolicy must not be null");

        requireManageDiscountPoliciesPermission(actorId, companyId);
        requireDiscountPolicyBelongsToCompany(discountPolicy, companyId);
        requireCompanyOwnsScopeEvents(companyId, discountPolicy.scope());

        requireDiscountPolicyCompatibleWithActivePurchasePolicies(discountPolicy);

        discountPolicyRepository.save(discountPolicy);

        logger.info(
                "Discount policy saved. policyId={}, companyId={}",
                discountPolicy.id(),
                companyId
        );
    }

    public void activateDiscountPolicy(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId
    ) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManageDiscountPoliciesPermission(actorId, companyId);

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        policy.activate();

        requireDiscountPolicyCompatibleWithActivePurchasePolicies(policy);

        discountPolicyRepository.save(policy);

        logger.info(
                "Discount policy activated. policyId={}, companyId={}",
                policy.id(),
                companyId
        );
    }

    public void deactivateDiscountPolicy(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId
    ) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManageDiscountPoliciesPermission(actorId, companyId);

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        policy.deactivate();

        discountPolicyRepository.save(policy);

        logger.info(
                "Discount policy deactivated. policyId={}, companyId={}",
                policy.id(),
                companyId
        );
    }

    public void modifyDiscountPolicyScope(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId,
            PolicyScope newScope
    ) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(newScope, "newScope must not be null");

        requireManageDiscountPoliciesPermission(actorId, companyId);

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);
        requireCompanyOwnsScopeEvents(companyId, newScope);

        policy.changeScope(newScope);

        requireDiscountPolicyCompatibleWithActivePurchasePolicies(policy);

        discountPolicyRepository.save(policy);

        logger.info(
                "Discount policy scope modified. policyId={}, companyId={}, active={}",
                policy.id(),
                companyId,
                policy.isActive()
        );
    }

    public void setDiscountPolicyCompanyWide(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId
    ) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManageDiscountPoliciesPermission(actorId, companyId);

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        policy.setCompanyWide();

        requireDiscountPolicyCompatibleWithActivePurchasePolicies(policy);

        discountPolicyRepository.save(policy);

        logger.info(
                "Discount policy set to company-wide. policyId={}, companyId={}",
                policy.id(),
                companyId
        );
    }

    public void setDiscountPolicyNotCompanyWide(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId
    ) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManageDiscountPoliciesPermission(actorId, companyId);

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        policy.deactivateCompanyWide();

        discountPolicyRepository.save(policy);

        logger.info(
                "Discount policy set to not company-wide. policyId={}, companyId={}",
                policy.id(),
                companyId
        );
    }

    public void addEventToDiscountPolicy(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId,
            EventId eventId
    ) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManageDiscountPoliciesPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        policy.activateForEvent(eventId);

        requireDiscountPolicyCompatibleWithActivePurchasePolicies(policy);

        discountPolicyRepository.save(policy);

        logger.info(
                "Event added to discount policy. policyId={}, eventId={}",
                policy.id(),
                eventId
        );
    }

    public void removeEventFromDiscountPolicy(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId,
            EventId eventId
    ) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManageDiscountPoliciesPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        policy.deactivateForEvent(eventId);

        discountPolicyRepository.save(policy);

        logger.info(
                "Event removed from discount policy. policyId={}, eventId={}",
                policy.id(),
                eventId
        );
    }

    public void addDiscountToPolicy(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId,
            DiscountCommand command
    ) {
        Objects.requireNonNull(command, "command must not be null");

        Discount discount = policyCommandAssembler.toDiscount(command);

        addDiscountToPolicy(actorId, companyId, policyId, discount);
    }

    public void addDiscountToPolicy(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId,
            Discount discount
    ) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(discount, "discount must not be null");

        requireManageDiscountPoliciesPermission(actorId, companyId);

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        policy.addDiscount(discount);

        requireDiscountPolicyCompatibleWithActivePurchasePolicies(policy);

        discountPolicyRepository.save(policy);

        logger.info(
                "Discount added to policy. policyId={}, discountName={}",
                policy.id(),
                discount.getDiscountName()
        );
    }

    public void deleteDiscountPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManageDiscountPoliciesPermission(actorId, companyId);

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        discountPolicyRepository.deleteById(policyId);

        logger.info("Discount policy deleted. policyId={}, companyId={}", policyId, companyId);
    }

    public void clearAllDiscountsOfCompany(MemberId actorId, CompanyId companyId) {
        requireManageDiscountPoliciesPermission(actorId, companyId);

        List<DiscountPolicy> companyPolicies = discountPolicyRepository.findByCompanyId(companyId);

        for (DiscountPolicy policy : companyPolicies) {
            discountPolicyRepository.deleteById(policy.id());
        }

        logger.info(
                "All discount policies cleared for company. companyId={}, deletedCount={}",
                companyId,
                companyPolicies.size()
        );
    }

    public void deactivateAllCompanyDiscounts(MemberId actorId, CompanyId companyId) {
        requireManageDiscountPoliciesPermission(actorId, companyId);

        List<DiscountPolicy> companyPolicies = discountPolicyRepository.findByCompanyId(companyId);

        for (DiscountPolicy policy : companyPolicies) {
            if (policy.isActive()) {
                policy.deactivate();
                discountPolicyRepository.save(policy);
            }
        }

        logger.info("All discount policies deactivated for company. companyId={}", companyId);
    }

    public void removeEventFromAllDiscountScopes(
            MemberId actorId,
            CompanyId companyId,
            EventId eventId
    ) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManageDiscountPoliciesPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        List<DiscountPolicy> companyPolicies = discountPolicyRepository.findByCompanyId(companyId);

        for (DiscountPolicy policy : companyPolicies) {
            if (policy.scope().eventIds().contains(eventId)) {
                policy.deactivateForEvent(eventId);
                discountPolicyRepository.save(policy);
            }
        }

        logger.info(
                "Event removed from all discount policy scopes. companyId={}, eventId={}",
                companyId,
                eventId
        );
    }

    public void clearEventsFromAllDiscounts(MemberId actorId, CompanyId companyId) {
        requireManageDiscountPoliciesPermission(actorId, companyId);

        List<DiscountPolicy> companyPolicies = discountPolicyRepository.findByCompanyId(companyId);

        for (DiscountPolicy policy : companyPolicies) {
            policy.clearAllEventsFromScope();
            discountPolicyRepository.save(policy);
        }

        logger.info("Explicit event scopes cleared from all discount policies. companyId={}", companyId);
    }

    // ---------------------------------------------------------------------
    // Compatibility validation
    // ---------------------------------------------------------------------

    private void requirePurchasePolicyCompatibleWithActiveDiscounts(PurchasePolicy purchasePolicy) {
        Objects.requireNonNull(purchasePolicy, "purchasePolicy must not be null");

        if (!purchasePolicy.isActive()) {
            return;
        }

        List<DiscountPolicy> activeDiscountPolicies = discountPolicyRepository
                .findActiveByCompanyId(purchasePolicy.companyId())
                .stream()
                .filter(discountPolicy -> scopesMayOverlap(purchasePolicy.scope(), discountPolicy.scope()))
                .toList();

        for (DiscountPolicy discountPolicy : activeDiscountPolicies) {
            requireNoConflict(purchasePolicy, discountPolicy);
        }
    }

    private void requireDiscountPolicyCompatibleWithActivePurchasePolicies(DiscountPolicy discountPolicy) {
        Objects.requireNonNull(discountPolicy, "discountPolicy must not be null");

        if (!discountPolicy.isActive()) {
            return;
        }

        List<PurchasePolicy> activePurchasePolicies = purchasePolicyRepository
                .findActiveByCompanyId(discountPolicy.companyId())
                .stream()
                .filter(purchasePolicy -> scopesMayOverlap(purchasePolicy.scope(), discountPolicy.scope()))
                .toList();

        for (PurchasePolicy purchasePolicy : activePurchasePolicies) {
            requireNoConflict(purchasePolicy, discountPolicy);
        }
    }

    private void requireNoConflict(PurchasePolicy purchasePolicy, DiscountPolicy discountPolicy) {
        if (!purchasePolicy.companyId().equals(discountPolicy.companyId())) {
            return;
        }

        if (!scopesMayOverlap(purchasePolicy.scope(), discountPolicy.scope())) {
            return;
        }

        for (Discount discount : discountPolicy.discounts()) {
            PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(
                    purchasePolicy.policy(),
                    discount.policy()
            );

            if (!result.isSuccess()) {
                logger.warn(
                        "Policy conflict detected. purchasePolicyId={}, discountPolicyId={}, discountName={}, reason={}",
                        purchasePolicy.id(),
                        discountPolicy.id(),
                        discount.getDiscountName(),
                        result.reason()
                );

                throw new PolicyException(
                        "Policy conflict between purchase policy '"
                                + purchasePolicy.policyName()
                                + "' and discount '"
                                + discount.getDiscountName()
                                + "': "
                                + result.reason()
                );
            }
        }
    }

    private boolean scopesMayOverlap(PolicyScope first, PolicyScope second) {
        Objects.requireNonNull(first, "first scope must not be null");
        Objects.requireNonNull(second, "second scope must not be null");

        if (!first.isScopedToEventsOrCompany() || !second.isScopedToEventsOrCompany()) {
            return false;
        }

        if (first.isCompanyWide() || second.isCompanyWide()) {
            return true;
        }

        return first.eventIds()
                .stream()
                .anyMatch(second.eventIds()::contains);
    }

    @Override
    public List<EventId> getEventIdsWithActiveVisibleDiscounts() {
        logger.debug("Finding EventIds with active visible discounts");

        List<EventId> eventIds = discountPolicyRepository.findActiveWithVisibleDiscounts()
                .stream()
                .flatMap(policy -> {
                    if (policy.scope().isCompanyWide()) {
                        return eventOwnershipChecker.allEventsOfCompany(policy.companyId()).stream();
                    }

                    return policy.scope().eventIds().stream();
                })
                .distinct()
                .toList();

        logger.debug("Found {} EventIds with active visible discounts", eventIds.size());

        return eventIds;
    }

    // ---------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------

    private PurchasePolicy getPurchasePolicyOrThrow(PurchasePolicyId policyId) {
        return purchasePolicyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyException("Purchase policy not found: " + policyId));
    }

    private DiscountPolicy getDiscountPolicyOrThrow(DiscountPolicyId policyId) {
        return discountPolicyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyException("Discount policy not found: " + policyId));
    }

    private void requirePurchasePolicyBelongsToCompany(PurchasePolicy purchasePolicy, CompanyId companyId) {
        if (!purchasePolicy.companyId().equals(companyId)) {
            throw new SecurityException("Cannot manage purchase policy of another company");
        }
    }

    private void requireDiscountPolicyBelongsToCompany(DiscountPolicy discountPolicy, CompanyId companyId) {
        if (!discountPolicy.companyId().equals(companyId)) {
            throw new SecurityException("Cannot manage discount policy of another company");
        }
    }

    private void requireCompanyOwnsScopeEvents(CompanyId companyId, PolicyScope scope) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(scope, "scope must not be null");

        for (EventId eventId : scope.eventIds()) {
            requireCompanyOwnsEvent(companyId, eventId);
        }
    }

    private void requireCompanyOwnsEvent(CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        if (!eventOwnershipChecker.isEventByCompany(eventId, companyId)) {
            throw new SecurityException(
                    "event does not belong to company. eventId=" + eventId + ", companyId=" + companyId
            );
        }
    }

    private void requireCompanyOwnsAllEventsInSet(CompanyId companyId, Set<EventId> eventIds) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventIds, "eventIds must not be null");

        if (eventIds.isEmpty()) {
            throw new IllegalArgumentException("events must be non empty");
        }

        for (EventId eventId : eventIds) {
            requireCompanyOwnsEvent(companyId, eventId);
        }
    }

    private void requireManagePurchasePoliciesPermission(MemberId actorId, CompanyId companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (!permissionChecker.canManagePurchasePolicies(actorId, companyId)) {
            throw new SecurityException(
                    "actor is not allowed to manage purchase policies for company: " + companyId
            );
        }
    }

    private void requireManageDiscountPoliciesPermission(MemberId actorId, CompanyId companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (!permissionChecker.canManageDiscountPolicies(actorId, companyId)) {
            throw new SecurityException(
                    "actor is not allowed to manage discount policies for company: " + companyId
            );
        }
    }

     public PurchasePolicyId createCompanyWidePurchasePolicy(MemberId actorId, CompanyId companyId, String policyName, IPolicy policy) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(policyName, "policyName must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);

        PurchasePolicy purchasePolicy = new PurchasePolicy( 
                companyId,
                policyName,
                PolicyScope.companyWideScope(),
                policy
        );
        
        savePurchasePolicy(actorId, companyId, purchasePolicy);
        return purchasePolicy.id();
    }

    public PurchasePolicyId createEventScopedPurchasePolicy(MemberId actorId, EventId eventId, String policyName, IPolicy policy) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(policyName, "policyName must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        CompanyId companyId = eventOwnershipChecker.companyOfEvent(eventId);
        requireManagePurchasePoliciesPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        PurchasePolicy purchasePolicy = new PurchasePolicy(
                companyId,
                policyName,
                PolicyScope.forSingleEvent(eventId),
                policy
        );

        savePurchasePolicy(actorId, companyId, purchasePolicy);
        return purchasePolicy.id();
    }

    public DiscountPolicyId createCompanyWideDiscountPolicy(MemberId actorId, CompanyId companyId, String discountName, boolean isStackable,
                                                             BigDecimal discountPercent, IPolicy policy, boolean isVisible, LocalDate endDate) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(discountName, "discountName must not be null");
        Objects.requireNonNull(discountPercent, "discountPercent must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        requireManageDiscountPoliciesPermission(actorId, companyId);
        DiscountPolicy discountPolicy= new DiscountPolicy(DiscountPolicyId.random(),companyId,PolicyScope.companyWideScope());
        discountPolicy.addDiscount(new Discount(discountName, discountPercent, policy, isVisible, endDate));
        if (isStackable) {
            discountPolicy.allowStacking();
        }
        saveDiscountPolicy(actorId, companyId, discountPolicy);
        return discountPolicy.id();
    }

    public DiscountPolicyId createEventScopedDiscountPolicy(MemberId actorId, EventId eventId, String discountName, BigDecimal discountPercent, IPolicy policy,
                                                             boolean isVisible, LocalDate endDate, boolean stackable) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(discountName, "discountName must not be null");
        Objects.requireNonNull(discountPercent, "discountPercent must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        CompanyId companyId = eventOwnershipChecker.companyOfEvent(eventId);
        requireManageDiscountPoliciesPermission(actorId, companyId);
        DiscountPolicy discountPolicy= new DiscountPolicy(DiscountPolicyId.random(),companyId,PolicyScope.forSingleEvent(eventId));
        discountPolicy.addDiscount(new Discount(discountName, discountPercent, policy, isVisible, endDate));
        if (stackable) {
            discountPolicy.allowStacking();
        }
        
        saveDiscountPolicy(actorId, companyId, discountPolicy);
        return discountPolicy.id();
    }


    private DiscountPolicyId createNewDiscountPolicyForEvents (MemberId actorId, Set<EventId> events,
                                                                 List<Discount> discounts, boolean stackable) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) {
            throw new IllegalArgumentException("events must be non empty");
        }
        for (EventId eventId : events) {
            Objects.requireNonNull(eventId, "discount must not be null");
        }
        Objects.requireNonNull(discounts, "discounts must not be null");
        for (Discount discount : discounts) {
            Objects.requireNonNull(discount, "discount must not be null");
        }
        EventId sampleEventId = events.stream().findAny()
                                        .orElseThrow(() -> new IllegalArgumentException("No eventId found"));
        CompanyId companyId = eventOwnershipChecker.companyOfEvent(sampleEventId);
        requireCompanyOwnsAllEventsInSet(companyId, events);
        requireManageDiscountPoliciesPermission(actorId, companyId);

        DiscountPolicy discountPolicy = DiscountPolicy.inactiveForEvents(companyId, events);
        discountPolicy = DiscountPolicy.withDiscounts(discountPolicy, discounts);
        if (stackable) {
            discountPolicy.allowStacking();
        }

        discountPolicyRepository.save(discountPolicy);
        return discountPolicy.id();
    }

    private DiscountPolicyId createNewCompanyWideDiscountPolicy (MemberId actorId, CompanyId companyId,
                                                                 List<Discount> discounts, boolean stackable) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(discounts, "discounts must not be null");
        for (Discount discount : discounts) {
            Objects.requireNonNull(discount, "discount must not be null");
        }

        requireManageDiscountPoliciesPermission(actorId, companyId);

        DiscountPolicy discountPolicy = DiscountPolicy.inactiveCompanyWide(companyId);
        discountPolicy = DiscountPolicy.withDiscounts(discountPolicy, discounts);
        if (stackable) {
            discountPolicy.allowStacking();
        }

        discountPolicyRepository.save(discountPolicy);
        return discountPolicy.id();
    }


    public PurchasePolicyId createMinAgePurchasePolicyForEvent(MemberId actorId, EventId eventId,
                                                                 int minAge, String policyName) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(policyName, "policyName must not be null");

        IPolicy ageLimPolicy = new MinAgePolicy(minAge);
        return createEventScopedPurchasePolicy(actorId, eventId, policyName, ageLimPolicy);
    }

    
    public PurchasePolicyId createMinMaxTicketsPurchasePolicyForEvent(MemberId actorId, EventId eventId,
                                                                 int minAllowed, int maxAllowed, String policyName) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (minAllowed < 1 || maxAllowed < 1) {
            throw new IllegalArgumentException("minimum and maximum allowed must be higher than 0");
        }
        if (minAllowed > maxAllowed ) {
            throw new IllegalArgumentException("maximum cannot be less than minimum");
        }
        IPolicy allowedTicketAmountPolicy = new AndPolicy(List.of(new MinTicketPolicy(minAllowed), new MaxTicketPolicy(maxAllowed)));
        return createEventScopedPurchasePolicy(actorId, eventId, policyName, allowedTicketAmountPolicy);
    }


    public DiscountPolicyId createGeneralCompanyWideDiscount(MemberId actorId, CompanyId companyId, String discountName,
                                                             BigDecimal discountPercent, LocalDate endDate, boolean stackable) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(discountName, "discountName must not be null");
        List<Discount> discounts = List.of(Discount.GeneralDiscount(discountName, discountPercent, endDate));
        return createNewCompanyWideDiscountPolicy(actorId, companyId, discounts, stackable);
    }

    public DiscountPolicyId createCouponCompanyWideDiscount(MemberId actorId, CompanyId companyId, String discountName,
                                                             BigDecimal discountPercent, LocalDate endDate, boolean stackable, String code) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(discountName, "discountName must not be null");
        List<Discount> discounts = List.of(Discount.CouponDiscount(discountName, discountPercent, endDate, code));
        return createNewCompanyWideDiscountPolicy(actorId, companyId, discounts, stackable);
    }

}