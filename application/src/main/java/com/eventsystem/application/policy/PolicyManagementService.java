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
import com.eventsystem.domain.policy.Discount;
import com.eventsystem.domain.policy.DiscountPolicy;
import com.eventsystem.domain.policy.DiscountPolicyId;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PolicyConflictDetector;
import com.eventsystem.domain.policy.PolicyScope;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchasePolicy;
import com.eventsystem.domain.policy.PurchasePolicyId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

        PurchasePolicy policy = PurchasePolicy.AllowAll(
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

        PurchasePolicy policy = PurchasePolicy.NotAllowed(
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
}