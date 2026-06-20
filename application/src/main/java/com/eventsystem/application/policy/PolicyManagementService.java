package com.eventsystem.application.policy;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.policy.policybuilder.DiscountCommand;
import com.eventsystem.application.policy.policybuilder.DiscountPolicyCommand;
import com.eventsystem.application.policy.policybuilder.PolicyCommandAssembler;
import com.eventsystem.application.policy.policybuilder.PurchasePolicyCommand;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.policy.PolicyConflictDetector;
import com.eventsystem.domain.policy.discount.Discount;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.domain.policy.discount.IDiscountPolicyRepository;
import com.eventsystem.domain.policy.purchase.IPurchasePolicyRepository;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.domain.policy.rule.IPolicy;
import com.eventsystem.domain.policy.rule.basic.AlwaysTruePolicy;
import com.eventsystem.domain.policy.rule.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.rule.basic.MinAgePolicy;
import com.eventsystem.domain.policy.rule.basic.MinTicketPolicy;
import com.eventsystem.domain.policy.rule.basic.NeverAllowPolicy;
import com.eventsystem.domain.policy.rule.composite.AndPolicy;
import com.eventsystem.domain.policy.shared.PolicyOwnerType;
import com.eventsystem.domain.policy.shared.PolicyScope;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

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
            PolicyCommandAssembler policyCommandAssembler) {
        this.purchasePolicyRepository = Objects.requireNonNull(
                purchasePolicyRepository,
                "purchasePolicyRepository must not be null");
        this.discountPolicyRepository = Objects.requireNonNull(
                discountPolicyRepository,
                "discountPolicyRepository must not be null");
        this.permissionChecker = Objects.requireNonNull(
                permissionChecker,
                "permissionChecker must not be null");
        this.eventOwnershipChecker = Objects.requireNonNull(
                eventOwnershipChecker,
                "eventOwnershipChecker must not be null");
        this.policyCommandAssembler = Objects.requireNonNull(
                policyCommandAssembler,
                "policyCommandAssembler must not be null");
    }

    // ---------------------------------------------------------------------
    // Company Policy Creators - for company owned policies
    // ---------------------------------------------------------------------
    public PurchasePolicyId createNewCompanyWidePurchasePolicy(
            MemberId actorId,
            CompanyId companyId,
            String policyName,
            IPolicy policy) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(policyName, "policyName must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        logger.info("Creating company owned Purchase policy for company. companyId={}", companyId);
        requireManagePurchasePoliciesPermission(actorId, companyId);

        PurchasePolicy purchasePolicy = PurchasePolicy.companyPolicy(companyId, policyName,
                PolicyScope.companyWideScope(), policy);
        logger.info(
                "Company owned Purchase policy created. policyId={}, companyId={}, active={}",
                purchasePolicy.id(),
                purchasePolicy.companyId(),
                purchasePolicy.isActive());

        requirePurchasePolicyCompatibleWithActiveDiscounts(purchasePolicy);
        savePurchasePolicy(actorId, companyId, purchasePolicy);
        return purchasePolicy.id();
    }

    public DiscountPolicyId createNewCompanyWideDiscountPolicy(
            MemberId actorId,
            CompanyId companyId,
            String discountName,
            boolean isStackable,
            BigDecimal discountPercent,
            IPolicy policy,
            boolean isVisible,
            LocalDate endDate) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(discountName, "discountName must not be null");
        Objects.requireNonNull(discountPercent, "discountPercent must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        return createNewCompanyWideDiscountPolicy(actorId, companyId,
                List.of(new Discount(discountName, discountPercent, policy, isVisible, endDate)), isStackable);
    }

    public DiscountPolicyId createNewCompanyWideDiscountPolicy(MemberId actorId, CompanyId companyId,
            List<Discount> discounts,
            boolean isStackable) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(discounts, "discount must not be null");
        if (discounts.stream().anyMatch(discount -> discount == null)) {
            throw new DiscountPolicyException("Given discounts cannot be null");
        }

        requireManageDiscountPoliciesPermission(actorId, companyId);
        logger.info("Creating company owned Discount policy for company. companyId={}", companyId);

        DiscountPolicy discountPolicy = DiscountPolicy.companyPolicy(
                companyId,
                PolicyScope.companyWideScope(),
                discounts,
                isStackable,
                false);

        logger.info(
                "company owned Discount policy created. policyId={}, companyId={}",
                discountPolicy.id(),
                companyId);

        requireDiscountPolicyCompatibleWithActivePurchasePolicies(discountPolicy);
        saveDiscountPolicy(actorId, companyId, discountPolicy);
        return discountPolicy.id();
    }

    // ---------------------------------------------------------------------
    // Event owned Policy Creators - for event owned policies
    // ---------------------------------------------------------------------
    public PurchasePolicyId createNewEventOwnedPurchasePolicy(
            MemberId actorId,
            EventId eventId,
            String policyName,
            IPolicy policy) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(policyName, "policyName must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        CompanyId companyId = eventOwnershipChecker.companyOfEvent(eventId);

        requireManageEventPurchasePolicyPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        logger.info("Creating Event owned Purchase policy for Event. EventId={}", eventId);

        PurchasePolicy purchasePolicy = PurchasePolicy.eventPolicy(companyId, eventId, policyName, policy);

        logger.info(
                "Event owned Purchase policy created. policyId={}, companyId={}, eventId={}, active={}",
                purchasePolicy.id(),
                purchasePolicy.companyId(),
                eventId,
                purchasePolicy.isActive());

        requirePurchasePolicyCompatibleWithActiveDiscounts(purchasePolicy);
        savePurchasePolicy(actorId, companyId, purchasePolicy);
        return purchasePolicy.id();
    }

    public DiscountPolicyId createNewEventOwnedDiscountPolicy(
            MemberId actorId,
            EventId eventId,
            String discountName,
            BigDecimal discountPercent,
            IPolicy policy,
            boolean isVisible,
            LocalDate endDate,
            boolean stackable) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(discountName, "discountName must not be null");
        Objects.requireNonNull(discountPercent, "discountPercent must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        CompanyId companyId = eventOwnershipChecker.companyOfEvent(eventId);

        requireManageEventDiscountPolicyPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        return createNewEventOwnedDiscountPolicy(actorId, companyId, eventId,
                List.of(new Discount(discountName, discountPercent, policy, isVisible, endDate)), stackable);

    }

    public DiscountPolicyId createNewEventOwnedDiscountPolicy(MemberId actorId, CompanyId companyId, EventId eventId,
            List<Discount> discounts,
            boolean isStackable) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(discounts, "discount must not be null");
        if (discounts.stream().anyMatch(discount -> discount == null)) {
            throw new DiscountPolicyException("Given discounts cannot be null");
        }

        requireManageEventDiscountPolicyPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);
        logger.info("Creating Event owned Discount policy for Event. companyId={}, eventId={}", companyId, eventId);

        DiscountPolicy discountPolicy = DiscountPolicy.eventPolicy(
                companyId,
                eventId,
                discounts,
                isStackable,
                false);

        logger.info(
                "Event owned Discount policy created. policyId={}, companyId={}, eventId={}",
                discountPolicy.id(),
                companyId,
                eventId);

        requireDiscountPolicyCompatibleWithActivePurchasePolicies(discountPolicy);
        saveDiscountPolicy(actorId, companyId, discountPolicy);
        return discountPolicy.id();
    }

    // ---------------------------------------------------------------------
    // Purchase policy creation / update
    // ---------------------------------------------------------------------

    public PurchasePolicyId createPurchasePolicy(PurchasePolicyCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        MemberId actorId = new MemberId(command.actorId());
        CompanyId companyId = new CompanyId(command.companyId());

        PolicyOwnerType owner = policyCommandAssembler.toOwnerType(command.ownerType());
        PolicyScope scope = policyCommandAssembler.toScope(command.scope());

        if (owner == PolicyOwnerType.COMPANY) {
            requireManagePurchasePoliciesPermission(actorId, companyId);
        } else {
            requireManageEventPurchasePolicyPermission(actorId, companyId);
        }
        requireCompanyOwnsScopeEvents(companyId, scope);

        IPolicy rule = policyCommandAssembler.toPolicy(command.rule());

        PurchasePolicy purchasePolicy;

        if (owner == PolicyOwnerType.EVENT) {

            EventId eventId = requireSingleEventScope(scope);
            purchasePolicy = PurchasePolicy.eventPolicy(
                    companyId,
                    eventId,
                    command.policyName(),
                    rule);
        } else {
            purchasePolicy = PurchasePolicy.companyPolicy(
                    companyId,
                    command.policyName(),
                    scope,
                    rule);
        }

        requirePurchasePolicyCompatibleWithActiveDiscounts(purchasePolicy);
        purchasePolicyRepository.save(purchasePolicy);

        logger.info(
                "Purchase policy created. policyId={}, companyId={}, active={}",
                purchasePolicy.id(),
                purchasePolicy.companyId(),
                purchasePolicy.isActive());

        return purchasePolicy.id();
    }

    public void savePurchasePolicy(MemberId actorId, CompanyId companyId, PurchasePolicy purchasePolicy) {
        Objects.requireNonNull(purchasePolicy, "purchasePolicy must not be null");

        requirePurchasePolicyBelongsToCompany(purchasePolicy, companyId);
        requireManagePurchasePolicyPermission(actorId, companyId, purchasePolicy);
        requireCompanyOwnsScopeEvents(companyId, purchasePolicy.scope());

        requirePurchasePolicyCompatibleWithActiveDiscounts(purchasePolicy);

        purchasePolicyRepository.save(purchasePolicy);

        logger.info(
                "Purchase policy saved. policyId={}, companyId={}",
                purchasePolicy.id(),
                companyId);
    }

    public void modifyPurchasePolicyScope(
            MemberId actorId,
            CompanyId companyId,
            PurchasePolicyId policyId,
            PolicyScope newScope) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(newScope, "newScope must not be null");

        PurchasePolicy policy = getPurchasePolicyOrThrow(policyId);
        requirePurchasePolicyBelongsToCompany(policy, companyId);

        requireManagePurchasePolicyScopeChangePermission(
                actorId,
                companyId,
                policy.scope(),
                newScope);

        requireCompanyOwnsScopeEvents(companyId, newScope);

        policy.setScopeTo(newScope);

        requirePurchasePolicyCompatibleWithActiveDiscounts(policy);

        purchasePolicyRepository.save(policy);

        logger.info(
                "Purchase policy scope modified. policyId={}, companyId={}, active={}",
                policy.id(),
                companyId,
                policy.isActive());
    }

    public void setPurchasePolicyCompanyWide(
            MemberId actorId,
            CompanyId companyId,
            PurchasePolicyId policyId) {
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
                companyId);
    }

    public void setPurchasePolicyNotCompanyWide(
            MemberId actorId,
            CompanyId companyId,
            PurchasePolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);

        PurchasePolicy policy = getPurchasePolicyOrThrow(policyId);
        requirePurchasePolicyBelongsToCompany(policy, companyId);

        policy.deactivateCompanyWide();

        purchasePolicyRepository.save(policy);

        logger.info(
                "Purchase policy set to not company-wide. policyId={}, companyId={}",
                policy.id(),
                companyId);
    }

    public void addEventToPurchasePolicy(
            MemberId actorId,
            CompanyId companyId,
            PurchasePolicyId policyId,
            EventId eventId) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        PurchasePolicy policy = getPurchasePolicyOrThrow(policyId);
        requirePurchasePolicyBelongsToCompany(policy, companyId);

        requireManagePurchasePolicyPermission(actorId, companyId, policy);
        requireCompanyOwnsEvent(companyId, eventId);

        policy.activateForEvent(eventId);

        requirePurchasePolicyCompatibleWithActiveDiscounts(policy);

        purchasePolicyRepository.save(policy);

        logger.info(
                "Event added to purchase policy. policyId={}, eventId={}",
                policy.id(),
                eventId);
    }

    public void removeEventFromPurchasePolicy(
            MemberId actorId,
            CompanyId companyId,
            PurchasePolicyId policyId,
            EventId eventId) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        PurchasePolicy policy = getPurchasePolicyOrThrow(policyId);
        requirePurchasePolicyBelongsToCompany(policy, companyId);

        requireManagePurchasePolicyPermission(actorId, companyId, policy);
        requireCompanyOwnsEvent(companyId, eventId);

        policy.deactivateForEvent(eventId);

        purchasePolicyRepository.save(policy);

        logger.info(
                "Event removed from purchase policy. policyId={}, eventId={}",
                policy.id(),
                eventId);
    }

    public void renamePurchasePolicy(
            MemberId actorId,
            CompanyId companyId,
            PurchasePolicyId policyId,
            String newName) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(newName, "newName must not be null");

        PurchasePolicy policy = getPurchasePolicyOrThrow(policyId);
        requirePurchasePolicyBelongsToCompany(policy, companyId);

        requireManagePurchasePolicyPermission(actorId, companyId, policy);

        policy.setNameTo(newName);

        purchasePolicyRepository.save(policy);

        logger.info(
                "Purchase policy renamed. policyId={}, newName={}",
                policy.id(),
                newName);
    }

    public void deletePurchasePolicy(MemberId actorId, CompanyId companyId, PurchasePolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        PurchasePolicy policy = getPurchasePolicyOrThrow(policyId);
        requirePurchasePolicyBelongsToCompany(policy, companyId);

        requireManagePurchasePolicyPermission(actorId, companyId, policy);

        purchasePolicyRepository.deleteById(policyId);

        logger.info("Purchase policy deleted. policyId={}, companyId={}", policyId, companyId);
    }

    public void clearAllPurchasePoliciesOfCompany(MemberId actorId, CompanyId companyId) {
        requireManagePurchasePoliciesPermission(actorId, companyId);

        List<PurchasePolicy> companyPolicies = purchasePolicyRepository.findByCompanyId(companyId);

        for (PurchasePolicy policy : companyPolicies) {
            deletePurchasePolicy(actorId, companyId, policy.id());
        }

        logger.info(
                "All purchase policies cleared for company. companyId={}, deletedCount={}",
                companyId,
                companyPolicies.size());
    }

    private void deactivateAllPurchasePoliciesOfOwnerType(MemberId actorId, CompanyId companyId, PolicyOwnerType type) {
        requireManagePurchasePoliciesPermission(actorId, companyId);

        List<PurchasePolicy> companyPolicies = purchasePolicyRepository.findByCompanyId(companyId);

        boolean isCompanyPolicy = type == PolicyOwnerType.COMPANY;
        for (PurchasePolicy policy : companyPolicies) {
            if (policy.isActive() && policy.isCompanyPolicy() == isCompanyPolicy) {
                modifyPurchasePolicyScope(actorId, companyId, policy.id(), PolicyScope.clearScope());
            }
        }
        logger.info("All purchase policies deactivated for company. companyId={}", companyId);
    }

    public void deactivateAllCompanyPurchasePolicies(MemberId actorId, CompanyId companyId) {
        deactivateAllPurchasePoliciesOfOwnerType(actorId, companyId, PolicyOwnerType.COMPANY);
    }

    private void removeEventFromAllPurchasePolicyOfType(MemberId actorId, CompanyId companyId, EventId eventId,
            PolicyOwnerType type) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManageEventPurchasePolicyPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        List<PurchasePolicy> companyPolicies = purchasePolicyRepository.findByCompanyId(companyId);

        boolean isCompanyPolicy = type == PolicyOwnerType.COMPANY;
        for (PurchasePolicy policy : companyPolicies) {
            if (policy.scope().isListedIn(eventId) && policy.isCompanyPolicy() == isCompanyPolicy) {
                removeEventFromPurchasePolicy(actorId, companyId, policy.id(), eventId);
            }
        }

        logger.info(
                "Event removed from all purchase policy scopes. companyId={}, eventId={}",
                companyId,
                eventId);
    }

    public void removeEventFromAllCompanyPurchasePolicies(MemberId actorId, CompanyId companyId, EventId eventId) {
        removeEventFromAllPurchasePolicyOfType(actorId, companyId, eventId, PolicyOwnerType.COMPANY);
    }

    public void clearEventsFromAllCompanyPurchasePolicies(MemberId actorId, CompanyId companyId) {
        requireManagePurchasePoliciesPermission(actorId, companyId);

        List<PurchasePolicy> companyPolicies = purchasePolicyRepository.findByCompanyId(companyId);
        for (PurchasePolicy purchasePolicy : companyPolicies) {
            if (purchasePolicy.isCompanyPolicy()) {
                modifyPurchasePolicyScope(actorId, companyId, purchasePolicy.id(), PolicyScope.companyWideScope());
            }
        }

        logger.info("Explicit event scopes cleared from all purchase policies. companyId={}", companyId);
    }

    @Override
    public boolean isAffectedByActivePurchasePolicy(EventId eventId, CompanyId companyId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        return !purchasePolicyRepository.findActiveByCompanyId(companyId).isEmpty()
                || !purchasePolicyRepository.findApplicableToPurchase(eventOwnershipChecker.companyOfEvent(eventId), eventId).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Discount policy creation / update
    // ---------------------------------------------------------------------

    public DiscountPolicyId createDiscountPolicy(DiscountPolicyCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        MemberId actorId = new MemberId(command.actorId());
        CompanyId companyId = new CompanyId(command.companyId());

        PolicyOwnerType owner = policyCommandAssembler.toOwnerType(command.ownerType());
        PolicyScope scope = policyCommandAssembler.toScope(command.scope());

        if (owner == PolicyOwnerType.COMPANY) {
            requireManageDiscountPoliciesPermission(actorId, companyId);
        } else {
            requireManageEventDiscountPolicyPermission(actorId, companyId);
        }
        requireCompanyOwnsScopeEvents(companyId, scope);

        if (command.discounts() == null || command.discounts().isEmpty()) {
            throw new PolicyException("Discount policy must contain at least one discount");
        }

        List<Discount> discounts = command.discounts()
                .stream()
                .map(policyCommandAssembler::toDiscount)
                .toList();

        DiscountPolicy discountPolicy;

        if (owner == PolicyOwnerType.EVENT) {

            EventId eventId = requireSingleEventScope(scope);
            discountPolicy = DiscountPolicy.eventPolicy(
                    companyId,
                    eventId,
                    discounts,
                    command.stackable(),
                    command.activate());
        } else {
            discountPolicy = DiscountPolicy.companyPolicy(
                    companyId,
                    scope,
                    discounts,
                    command.stackable(),
                    command.activate());
        }
        requireDiscountPolicyCompatibleWithActivePurchasePolicies(discountPolicy);

        discountPolicyRepository.save(discountPolicy);

        logger.info(
                "Discount policy created. policyId={}, companyId={}, active={}, stackable={}, discountCount={}",
                discountPolicy.id(),
                discountPolicy.companyId(),
                discountPolicy.isActive(),
                discountPolicy.isStackable(),
                discountPolicy.discounts().size());

        return discountPolicy.id();
    }

    public void saveDiscountPolicy(MemberId actorId, CompanyId companyId, DiscountPolicy discountPolicy) {
        Objects.requireNonNull(discountPolicy, "discountPolicy must not be null");

        requireDiscountPolicyBelongsToCompany(discountPolicy, companyId);
        requireManageDiscountPolicyPermission(actorId, companyId, discountPolicy);
        requireCompanyOwnsScopeEvents(companyId, discountPolicy.scope());

        requireDiscountPolicyCompatibleWithActivePurchasePolicies(discountPolicy);

        discountPolicyRepository.save(discountPolicy);

        logger.info(
                "Discount policy saved. policyId={}, companyId={}",
                discountPolicy.id(),
                companyId);
    }

    public void activateDiscountPolicy(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        requireManageDiscountPolicyPermission(actorId, companyId, policy);

        policy.activate();

        requireDiscountPolicyCompatibleWithActivePurchasePolicies(policy);

        discountPolicyRepository.save(policy);

        logger.info(
                "Discount policy activated. policyId={}, companyId={}",
                policy.id(),
                companyId);
    }

    public void deactivateDiscountPolicy(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        requireManageDiscountPolicyPermission(actorId, companyId, policy);

        policy.deactivate();

        discountPolicyRepository.save(policy);

        logger.info(
                "Discount policy deactivated. policyId={}, companyId={}",
                policy.id(),
                companyId);
    }

    public void modifyDiscountPolicyScope(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId,
            PolicyScope newScope) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(newScope, "newScope must not be null");

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        requireManageDiscountPolicyScopeChangePermission(
                actorId,
                companyId,
                policy.scope(),
                newScope);

        requireCompanyOwnsScopeEvents(companyId, newScope);

        policy.changeScope(newScope);

        requireDiscountPolicyCompatibleWithActivePurchasePolicies(policy);

        discountPolicyRepository.save(policy);

        logger.info(
                "Discount policy scope modified. policyId={}, companyId={}, active={}",
                policy.id(),
                companyId,
                policy.isActive());
    }

    public void setDiscountPolicyCompanyWide(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId) {
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
                companyId);
    }

    public void setDiscountPolicyNotCompanyWide(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManageDiscountPoliciesPermission(actorId, companyId);

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        policy.deactivateCompanyWide();

        discountPolicyRepository.save(policy);

        logger.info(
                "Discount policy set to not company-wide. policyId={}, companyId={}",
                policy.id(),
                companyId);
    }

    public void addEventToDiscountPolicy(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId,
            EventId eventId) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        requireManageDiscountPolicyPermission(actorId, companyId, policy);
        requireCompanyOwnsEvent(companyId, eventId);

        policy.activateForEvent(eventId);

        requireDiscountPolicyCompatibleWithActivePurchasePolicies(policy);

        discountPolicyRepository.save(policy);

        logger.info(
                "Event added to discount policy. policyId={}, eventId={}",
                policy.id(),
                eventId);
    }

    public void removeEventFromDiscountPolicy(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId,
            EventId eventId) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        requireManageDiscountPolicyPermission(actorId, companyId, policy);
        requireCompanyOwnsEvent(companyId, eventId);

        policy.deactivateForEvent(eventId);

        discountPolicyRepository.save(policy);

        logger.info(
                "Event removed from discount policy. policyId={}, eventId={}",
                policy.id(),
                eventId);
    }

    public void addDiscountToPolicy(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId,
            DiscountCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Discount discount = policyCommandAssembler.toDiscount(command);

        addDiscountToPolicy(actorId, companyId, policyId, discount);
    }

    public void addDiscountToPolicy(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicyId policyId,
            Discount discount) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(discount, "discount must not be null");

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        requireManageDiscountPolicyPermission(actorId, companyId, policy);

        policy.addDiscount(discount);

        requireDiscountPolicyCompatibleWithActivePurchasePolicies(policy);

        discountPolicyRepository.save(policy);

        logger.info(
                "Discount added to policy. policyId={}, discountName={}",
                policy.id(),
                discount.getDiscountName());
    }

    public void deleteDiscountPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        DiscountPolicy policy = getDiscountPolicyOrThrow(policyId);
        requireDiscountPolicyBelongsToCompany(policy, companyId);

        requireManageDiscountPolicyPermission(actorId, companyId, policy);

        discountPolicyRepository.deleteById(policyId);

        logger.info("Discount policy deleted. policyId={}, companyId={}", policyId, companyId);
    }

    public void clearAllDiscountsOfCompany(MemberId actorId, CompanyId companyId) {
        requireManageDiscountPoliciesPermission(actorId, companyId);

        List<DiscountPolicy> companyPolicies = discountPolicyRepository.findByCompanyId(companyId);

        for (DiscountPolicy policy : companyPolicies) {
            if (policy.isCompanyPolicy()) {
                deleteDiscountPolicy(actorId, companyId, policy.id());
            }
        }

        logger.info(
                "All discount policies cleared for company. companyId={}, deletedCount={}",
                companyId,
                companyPolicies.size());
    }

    public void deactivateAllDiscountsOfType(MemberId actorId, CompanyId companyId, PolicyOwnerType type) {
        requireManageDiscountPoliciesPermission(actorId, companyId);

        List<DiscountPolicy> companyPolicies = discountPolicyRepository.findByCompanyId(companyId);

        boolean isCompanyPolicy = type == PolicyOwnerType.COMPANY;
        for (DiscountPolicy policy : companyPolicies) {
            if (policy.isActive() && policy.isCompanyPolicy() == isCompanyPolicy) {
                policy.deactivate();
                discountPolicyRepository.save(policy);
            }
        }

        logger.info("All discount policies deactivated for company. companyId={}", companyId);
    }

    public void deactivateAllCompanyDiscounts(MemberId actorId, CompanyId companyId) {
        requireManageDiscountPoliciesPermission(actorId, companyId);
        deactivateAllDiscountsOfType(actorId, companyId, PolicyOwnerType.COMPANY);
    }

    public void deactivateAllEventOwnedDiscounts(MemberId actorId, CompanyId companyId) {
        requireManageDiscountPoliciesPermission(actorId, companyId);
        deactivateAllDiscountsOfType(actorId, companyId, PolicyOwnerType.EVENT);
    }

    public void removeEventFromAllCompanyDiscountScopes(MemberId actorId, CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        requireManageEventDiscountPolicyPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        List<DiscountPolicy> companyPolicies = discountPolicyRepository.findByCompanyId(companyId);

        for (DiscountPolicy policy : companyPolicies) {
            if (policy.scope().isListedIn(eventId) && policy.isCompanyPolicy()) {
                removeEventFromDiscountPolicy(actorId, companyId, policy.id(), eventId);
                discountPolicyRepository.save(policy);
            }
        }

        logger.info(
                "Event removed from all discount policy scopes. companyId={}, eventId={}",
                companyId,
                eventId);
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
                    discount.policy());

            if (!result.isSuccess()) {
                logger.warn(
                        "Policy conflict detected. purchasePolicyId={}, discountPolicyId={}, discountName={}, reason={}",
                        purchasePolicy.id(),
                        discountPolicy.id(),
                        discount.getDiscountName(),
                        result.reason());

                throw new PolicyException(
                        "Policy conflict between purchase policy '"
                                + purchasePolicy.policyName()
                                + "' and discount '"
                                + discount.getDiscountName()
                                + "': "
                                + result.reason());
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

    private EventId requireSingleEventScope(PolicyScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");

        if (!scope.isForSingleEvent()) {
            throw new PolicyException("scope of event owned policy must be a single event id");
        }

        return scope.eventIds().iterator().next();
    }

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
                    "event does not belong to company. eventId=" + eventId + ", companyId=" + companyId);
        }
    }

    private void requireManagePurchasePoliciesPermission(MemberId actorId, CompanyId companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (!permissionChecker.canManagePurchasePolicies(actorId, companyId)) {
            throw new SecurityException(
                    "actor is not allowed to manage purchase policies for company: " + companyId);
        }
    }

    private void requireManageDiscountPoliciesPermission(MemberId actorId, CompanyId companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (!permissionChecker.canManageDiscountPolicies(actorId, companyId)) {
            throw new SecurityException(
                    "actor is not allowed to manage discount policies for company: " + companyId);
        }
    }

    private void requireManageEventDiscountPolicyPermission(MemberId actorId, CompanyId companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (!permissionChecker.canManageDiscountPolicies(actorId, companyId)
                && !permissionChecker.canManageEvents(actorId, companyId)) {
            throw new SecurityException(
                    "actor is not allowed to manage event discount policies for company: " + companyId);
        }
    }

    private void requireManageEventPurchasePolicyPermission(MemberId actorId, CompanyId companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (!permissionChecker.canManagePurchasePolicies(actorId, companyId)
                && !permissionChecker.canManageEvents(actorId, companyId)) {
            throw new SecurityException(
                    "actor is not allowed to manage event purchase policies for company: " + companyId);
        }
    }

    private void requireManagePurchasePolicyPermission(
            MemberId actorId,
            CompanyId companyId,
            PurchasePolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");

        if (policy.isCompanyPolicy()) {
            requireManagePurchasePoliciesPermission(actorId, companyId);
        } else {
            requireManageEventPurchasePolicyPermission(actorId, companyId);
        }
    }

    private void requireManageDiscountPolicyPermission(
            MemberId actorId,
            CompanyId companyId,
            DiscountPolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");

        if (policy.isCompanyPolicy()) {
            requireManageDiscountPoliciesPermission(actorId, companyId);
        } else {
            requireManageEventDiscountPolicyPermission(actorId, companyId);
        }
    }

    private void requireManagePurchasePolicyScopeChangePermission(
            MemberId actorId,
            CompanyId companyId,
            PolicyScope currentScope,
            PolicyScope newScope) {
        Objects.requireNonNull(currentScope, "currentScope must not be null");
        Objects.requireNonNull(newScope, "newScope must not be null");

        if (currentScope.isCompanyWide() || newScope.isCompanyWide()) {
            requireManagePurchasePoliciesPermission(actorId, companyId);
        } else {
            requireManageEventPurchasePolicyPermission(actorId, companyId);
        }
    }

    private void requireManageDiscountPolicyScopeChangePermission(
            MemberId actorId,
            CompanyId companyId,
            PolicyScope currentScope,
            PolicyScope newScope) {
        Objects.requireNonNull(currentScope, "currentScope must not be null");
        Objects.requireNonNull(newScope, "newScope must not be null");

        if (currentScope.isCompanyWide() || newScope.isCompanyWide()) {
            requireManageDiscountPoliciesPermission(actorId, companyId);
        } else {
            requireManageEventDiscountPolicyPermission(actorId, companyId);
        }
    }

    @Override
    public void createNewAllowAllPurchasePolicy(MemberId actorId, CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        requireCompanyOwnsEvent(companyId, eventId);

        createNewEventOwnedPurchasePolicy(actorId, eventId, "AllowAll", AlwaysTruePolicy.INSTANCE);
    }

    @Override
    public void createNotAllowedPurchasePolicy(MemberId actorId, CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        requireCompanyOwnsEvent(companyId, eventId);

        createNewEventOwnedPurchasePolicy(actorId, eventId, "NotAllowed", NeverAllowPolicy.INSTANCE);
    }

    public PurchasePolicyId createMinAgePurchasePolicyForEvent(
            MemberId actorId,
            EventId eventId,
            int minAge,
            String policyName) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(policyName, "policyName must not be null");

        IPolicy ageLimPolicy = new MinAgePolicy(minAge);
        return createNewEventOwnedPurchasePolicy(actorId, eventId, policyName, ageLimPolicy);
    }

    public PurchasePolicyId createMinMaxTicketsPurchasePolicyForEvent(
            MemberId actorId,
            EventId eventId,
            int minAllowed,
            int maxAllowed,
            String policyName) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(policyName, "policyName must not be null");

        if (minAllowed < 1 || maxAllowed < 1) {
            throw new IllegalArgumentException("minimum and maximum allowed must be higher than 0");
        }

        if (minAllowed > maxAllowed) {
            throw new IllegalArgumentException("maximum cannot be less than minimum");
        }

        IPolicy allowedTicketAmountPolicy = new AndPolicy(
                List.of(
                        new MinTicketPolicy(minAllowed),
                        new MaxTicketPolicy(maxAllowed)));

        return createNewEventOwnedPurchasePolicy(actorId, eventId, policyName, allowedTicketAmountPolicy);
    }

    public DiscountPolicyId createGeneralCompanyWideDiscount(
            MemberId actorId,
            CompanyId companyId,
            String discountName,
            BigDecimal discountPercent,
            LocalDate endDate,
            boolean stackable) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(discountName, "discountName must not be null");

        List<Discount> discounts = List.of(
                Discount.GeneralDiscount(discountName, discountPercent, endDate));

        return createNewCompanyWideDiscountPolicy(actorId, companyId, discounts, stackable);
    }

    public DiscountPolicyId createCouponCompanyWideDiscount(
            MemberId actorId,
            CompanyId companyId,
            String discountName,
            BigDecimal discountPercent,
            LocalDate endDate,
            boolean stackable,
            String code) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(discountName, "discountName must not be null");

        List<Discount> discounts = List.of(
                Discount.CouponDiscount(discountName, discountPercent, endDate, code));

        return createNewCompanyWideDiscountPolicy(actorId, companyId, discounts, stackable);
    }

}