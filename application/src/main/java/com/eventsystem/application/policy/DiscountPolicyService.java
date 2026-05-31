package com.eventsystem.application.policy;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.member.IMemberInformationPort;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.order.BuyerReference;



import com.eventsystem.domain.policy.Discount;
import com.eventsystem.domain.policy.DiscountPolicy;
import com.eventsystem.domain.policy.DiscountPolicyId;
import com.eventsystem.domain.policy.DiscountSummary;
import com.eventsystem.domain.policy.PolicyScope;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.member.MemberId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class DiscountPolicyService implements IDiscountApplicationPort {

    private static final Logger logger = LoggerFactory.getLogger(DiscountPolicyService.class);

    private final IDiscountPolicyRepository discountPolicyRepository;
    private final ICompanyPermissionServicePort permissionChecker;
    private final IEventManagementPort eventOwnershipChecker;
    private final IMemberInformationPort memberInfoPort;

    public DiscountPolicyService(IDiscountPolicyRepository discountPolicyRepository,
                                 ICompanyPermissionServicePort permissionChecker,
                                  IEventManagementPort eventOwnershipChecker, 
                                    IMemberInformationPort memberInfoPort) {
        this.discountPolicyRepository = Objects.requireNonNull(discountPolicyRepository, "discountPolicyRepository must not be null");
        this.permissionChecker = Objects.requireNonNull(permissionChecker, "permissionChecker must not be null");
        this.eventOwnershipChecker = Objects.requireNonNull(eventOwnershipChecker, "eventOwnershipChecker must not be null");
        this.memberInfoPort = Objects.requireNonNull(memberInfoPort, "eventOwnershipChecker must not be null");


    }

    public void saveDiscountPolicy(MemberId actorId, CompanyId companyId, DiscountPolicy discountPolicy) {
        requireManageDiscountsPermission(actorId, companyId);
        Objects.requireNonNull(discountPolicy, "discountPolicy must not be null");

        CompanyId requestedCompanyId = companyId;

        if (!discountPolicy.companyId().equals(requestedCompanyId)) {
            logger.warn(
                    "Cannot save discount policy for different company. requestedCompanyId={}, policyCompanyId={}, policyId={}",
                    requestedCompanyId,
                    discountPolicy.companyId(),
                    discountPolicy.id()
            );

            throw new SecurityException("Cannot save Discount policy for another company");
        }

        logger.debug(
                "Saving discount policy. policyId={}, companyId={}, active={}",
                discountPolicy.id(),
                discountPolicy.companyId(),
                discountPolicy.isActive()
        );

        discountPolicyRepository.save(discountPolicy);

        logger.info(
                "Discount policy saved. policyId={}, companyId={}",
                discountPolicy.id(),
                discountPolicy.companyId()
        );
    }

    public Optional<DiscountPolicy> findById(DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        logger.debug("Finding discount policy by id. policyId={}", policyId);

        return discountPolicyRepository.findById(policyId);
    }

    public DiscountPolicy getByIdOrThrow(DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        return discountPolicyRepository.findById(policyId)
                .orElseThrow(() -> {
                    logger.warn("Discount policy not found. policyId={}", policyId);
                    return new PolicyException("Discount policy not found: " + policyId);
                });
    }

    public List<DiscountPolicy> findByCompanyId(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");

        logger.debug("Finding discount policies by company. companyId={}", companyId);

        return discountPolicyRepository.findByCompanyId(companyId);
    }

    public List<DiscountPolicy> findActiveByCompanyId(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");

        logger.debug("Finding active discount policies by company. companyId={}", companyId);

        return discountPolicyRepository.findActiveByCompanyId(companyId);
    }

    public List<DiscountPolicy> findApplicableToPurchase(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        logger.debug(
                "Finding discount policies applicable to purchase. companyId={}, eventId={}",
                eventId.value()
        );

        return discountPolicyRepository.findApplicableToEvent(eventId);
    }

    public List<DiscountPolicy> findApplicableToPurchase(CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        logger.debug(
                "Finding discount policies applicable to purchase. companyId={}, eventId={}",
                companyId,
                eventId.value()
        );

        return discountPolicyRepository.findApplicableToPurchase(companyId, eventId);
    }

    public Set<EventId> getAllActiveDiscountEvents() {
        logger.debug("Finding all EventIds with active discount policies");

        List<DiscountPolicy> activeDiscountPolicies = discountPolicyRepository.findActive();

        List<EventId> eventIdsFromScopes = activeDiscountPolicies.stream()
                .flatMap(policy -> policy.scope().eventIds().stream())
                .toList();

        List<EventId> eventIdsFromCompanies = activeDiscountPolicies.stream()
                .filter(policy -> policy.scope().isCompanyWide())
                .flatMap(policy -> eventOwnershipChecker.allEventsOfCompany(policy.companyId()).stream())
                .toList();

        List<EventId> eventIds = Stream.concat(eventIdsFromScopes.stream(), eventIdsFromCompanies.stream())
                .distinct()
                .toList();

        logger.debug("Found {} EventIds with active discount policies", eventIds.size());

        return Set.copyOf(eventIds);
    }

    public void deleteDiscountPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManageDiscountsPermission(actorId, companyId);
        requireCompanyOwnsDiscountPolicy(policyId, companyId);

        logger.info("Deleting discount policy. policyId={}", policyId);

        discountPolicyRepository.deleteById(policyId);

        logger.info("Discount policy deleted. policyId={}", policyId);
    }

    public boolean existsById(DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        boolean exists = discountPolicyRepository.existsById(policyId);

        logger.debug("Checked discount policy existence. policyId={}, exists={}", policyId, exists);

        return exists;
    }

    public void clearAllDiscountsOfCompany(MemberId actorId, CompanyId companyId) {
        requireManageDiscountsPermission(actorId, companyId);

        logger.info("Clearing all discount policies of company. companyId={}", companyId);

        List<DiscountPolicy> companyPolicies = discountPolicyRepository.findByCompanyId(companyId);

        for (DiscountPolicy policy : companyPolicies) {
            discountPolicyRepository.deleteById(policy.id());

            logger.debug(
                    "Deleted discount policy as part of company clear. companyId={}, policyId={}",
                    companyId,
                    policy.id()
            );
        }

        logger.info(
                "Finished clearing all discount policies of company. companyId={}, deletedCount={}",
                companyId,
                companyPolicies.size()
        );

    }

    public void deactivateAllCompanyDiscounts(MemberId actorId, CompanyId companyId) {
        requireManageDiscountsPermission(actorId, companyId);

        logger.info("Deactivating all discount policies of company. companyId={}", companyId);

        List<DiscountPolicy> companyPolicies = discountPolicyRepository.findByCompanyId(companyId);

        for (DiscountPolicy policy : companyPolicies) {
            if (policy.isActive()) {
                policy.deactivate();
                discountPolicyRepository.save(policy);

                logger.debug(
                        "Deactivated discount policy as part of company deactivation. companyId={}, policyId={}",
                        companyId,
                        policy.id()
                );
            }
        }

        logger.info(
                "Finished deactivating company discount policies. companyId={}",
                companyId
        );
    }

    public void removeEventFromAllDiscountScopes(MemberId actorId, CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManageDiscountsPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);


        logger.info(
                "Removing event from all discount policy scopes. companyId={}, eventId={}",
                companyId,
                eventId.value()
        );

        List<DiscountPolicy> companyPolicies = discountPolicyRepository.findByCompanyId(companyId);

        for (DiscountPolicy policy : companyPolicies) {
            if (policy.scope().eventIds().contains(eventId)) {
                policy.deactivateForEvent(eventId);
                discountPolicyRepository.save(policy);
            }
        }

        logger.info(
                "Event removed from all discount Policy scopes. companyId={}, eventId={}",
                companyId,
                eventId.value()
        );
    }


    public void clearEventsFromAllDiscounts(MemberId actorId, CompanyId companyId) {
        requireManageDiscountsPermission(actorId, companyId);

        logger.info("Clearing all explicit event scopes from company discount policies. companyId={}", companyId);

        List<DiscountPolicy> companyPolicies = discountPolicyRepository.findByCompanyId(companyId);

        for (DiscountPolicy policy : companyPolicies) {
            policy.clearAllEventsFromScope();
            discountPolicyRepository.save(policy);
        }
    }

    public void modifyDiscountPolicyScope(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId, PolicyScope newScope) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(newScope, "newScope must not be null");

        requireManageDiscountsPermission(actorId, companyId);
        requireCompanyOwnsDiscountPolicy(policyId, companyId);

        DiscountPolicy policy = getByIdOrThrow(policyId);
        for (EventId eventId : newScope.eventIds()) {
            requireCompanyOwnsEvent(companyId, eventId);
        }
        policy.changeScope(newScope);
        discountPolicyRepository.save(policy);
    }

    public void setToCompanyWide(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManageDiscountsPermission(actorId, companyId);
        requireCompanyOwnsDiscountPolicy(policyId, companyId);

        logger.info("Changing discount policy scope to COMPANY WIDE. policyId={}", policyId);
        DiscountPolicy policy = getByIdOrThrow(policyId);
        policy.setCompanyWide();
        discountPolicyRepository.save(policy);
        logger.info("Discount policy scope set to COMPANY WIDE. policyId={}", policyId);
    }

    public void setToNotCompanyWide(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManageDiscountsPermission(actorId, companyId);
        requireCompanyOwnsDiscountPolicy(policyId, companyId);

        logger.info("Changing discount policy scope to NOT COMPANY WIDE. policyId={}", policyId);
        DiscountPolicy policy = getByIdOrThrow(policyId);
        policy.deactivateCompanyWide();
        discountPolicyRepository.save(policy);
        logger.info("Discount policy scope set to NOT COMPANY WIDE. policyId={}", policyId);
    }

    public void removeEventFromPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId,EventId eventId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManageDiscountsPermission(actorId, companyId);
        requireCompanyOwnsDiscountPolicy(policyId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        logger.info(
                "removing Event to discount policy. policyId={}, eventId={}",
                policyId,
                eventId
        );

        DiscountPolicy policy = getByIdOrThrow(policyId);
        policy.deactivateForEvent(eventId);;
        discountPolicyRepository.save(policy);
        logger.info(
                "Event removed to discount policy. policyId={}, eventId={}",
                policyId,
                eventId
        );
    }

    public void addEventToPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId,EventId eventId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManageDiscountsPermission(actorId, companyId);
        requireCompanyOwnsDiscountPolicy(policyId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);


        logger.info(
                "Adding Event to discount policy. policyId={}, eventId={}",
                policyId,
                eventId
        );

        DiscountPolicy policy = getByIdOrThrow(policyId);
        policy.activateForEvent(eventId);;
        discountPolicyRepository.save(policy);
        logger.info(
                "Event Added to discount policy. policyId={}, eventId={}",
                policyId,
                eventId
        );
    }

    public void activateDiscountPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManageDiscountsPermission(actorId, companyId);
        requireCompanyOwnsDiscountPolicy(policyId, companyId);

        logger.info("Activating discount policy. policyId={}", policyId);

        DiscountPolicy policy = getByIdOrThrow(policyId);
        policy.activate();
        discountPolicyRepository.save(policy);

        logger.info(
                "Discount policy activated. policyId={}, companyId={}",
                policy.id(),
                policy.companyId()
        );
    }

    public void deactivateDiscountPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManageDiscountsPermission(actorId, companyId);
        requireCompanyOwnsDiscountPolicy(policyId, companyId);

        logger.info("Deactivating discount policy. policyId={}", policyId);

        DiscountPolicy policy = getByIdOrThrow(policyId);
        policy.deactivate();
        discountPolicyRepository.save(policy);

        logger.info(
                "Discount policy deactivated. policyId={}, companyId={}",
                policy.id(),
                policy.companyId()
        );
    }

    public void addDiscountToPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId, Discount discount) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(discount, "discount must not be null");

        requireManageDiscountsPermission(actorId, companyId);
        requireCompanyOwnsDiscountPolicy(policyId, companyId);

        logger.info(
                "Adding discount to policy. policyId={}, discountName={}",
                policyId,
                discount.getDiscountName()
        );

        DiscountPolicy policy = getByIdOrThrow(policyId);
        policy.addDiscount(discount);
        discountPolicyRepository.save(policy);

        logger.info(
                "Discount added to policy. policyId={}, discountName={}",
                policy.id(),
                discount.getDiscountName()
        );
    }

    @Override
    public boolean doesDiscountApplyFor(PurchaseContext context) {
        Objects.requireNonNull(context, "context must not be null");

        logger.debug(
                "Checking if discount applies. companyId={}, eventId={}",
                context.companyId(),
                context.eventId().value()
        );

        return discountPolicyRepository
                .findApplicableToPurchase(context.companyId(), context.eventId())
                .stream()
                .anyMatch(policy -> policy.isPurchaseEligibleForDiscount(context));
    }

    @Override
    public DiscountSummary calculateDiscountSummary(PurchaseContext context, Money baseTotal) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(baseTotal, "baseCost must not be null");

        CompanyId companyId = context.companyId();
        EventId eventId = context.eventId();

        logger.debug(
                "Calculating discount summary. companyId={}, eventId={}, baseCost={}",
                companyId,
                eventId.value(),
                baseTotal
        );

        List<DiscountPolicy> applicablePolicies = discountPolicyRepository.findApplicableToPurchase(companyId, eventId);

        if (applicablePolicies.isEmpty()) {
            logger.info(
                    "No applicable discount policies found. companyId={}, eventId={}",
                    companyId,
                    eventId.value()
            );
            return DiscountSummary.NoDiscountSummary();
        }

        DiscountSummary bestSummary = calculateBestSummary(applicablePolicies, context, baseTotal);

        logger.info(
                "Discount summary calculated. companyId={}, eventId={}, totalDiscount={}",
                companyId,
                eventId.value(),
                bestSummary.totalDiscount()
        );

        return bestSummary;
    }

    @Override
    public DiscountSnapshot generateDiscountSnapshot(PurchaseContext context, Money baseCost) {

        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(baseCost, "baseCost must not be null");

        DiscountSummary summary = calculateDiscountSummary(context, baseCost);

        String discountName = summary.appliedDiscountsNames().isEmpty()
                ? "No Discount"
                : String.join(" ; ", summary.appliedDiscountsNames());

        DiscountSnapshot snapshot = new DiscountSnapshot(
                discountName,
                Money.of(summary.totalDiscount(), baseCost.currency())
        );

        logger.info(
                "Discount snapshot generated. eventId={}, discountAmount={}",
                context.eventId().value(),
                snapshot.discountAmount()
        );

        return snapshot;
    }

    @Override
    @Deprecated
    public DiscountSnapshot applyDiscount(String eventId, String discountCode, Money baseTotal) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(baseTotal, "baseTotal must not be null");

        logger.warn(
                "Using deprecated applyDiscount(eventId, discountCode, baseTotal). " +
                        "Prefer generateDiscountSnapshot(PurchaseContext, Money). eventId={}",
                eventId
        );

        EventId parsedEventId = new EventId(eventId);

        List<DiscountPolicy> applicablePolicies =
                discountPolicyRepository.findApplicableToEvent(parsedEventId);

        if (applicablePolicies.isEmpty()) {
            return new DiscountSnapshot(
                    "No Discount",
                    Money.of(BigDecimal.ZERO, baseTotal.currency())
            );
        }

        DiscountSummary bestSummary = DiscountSummary.NoDiscountSummary();

        for (DiscountPolicy policy : applicablePolicies) {
            PurchaseContext legacyContext = PurchaseContext.fromPurchaseInfo(
                    parsedEventId,
                    policy.companyId(),
                    List.of(),
                    LocalDate.now(),
                    discountCode
                );

            DiscountSummary currentSummary = policy.getFullDiscountSummary(legacyContext, baseTotal);

            if (currentSummary.totalDiscount().compareTo(bestSummary.totalDiscount()) > 0) {
                bestSummary = currentSummary;
            }
        }

        String discountName = bestSummary.appliedDiscountsNames().isEmpty()
                ? "No Discount"
                : String.join(" ; ", bestSummary.appliedDiscountsNames());

        return new DiscountSnapshot(
                discountName,
                Money.of(bestSummary.totalDiscount(), baseTotal.currency())
        );
    }

    private DiscountSummary calculateBestSummary(List<DiscountPolicy> policies,
                                                 PurchaseContext context,
                                                 Money baseCost) {
        DiscountSummary bestSummary = DiscountSummary.NoDiscountSummary();

        for (DiscountPolicy policy : policies) {
            DiscountSummary currentSummary = policy.getFullDiscountSummary(context, baseCost);

            if (currentSummary.totalDiscount().compareTo(bestSummary.totalDiscount()) > 0) {
                bestSummary = currentSummary;
            }
        }

        return bestSummary;
    }

    

    private void requireManageDiscountsPermission(MemberId actorId, CompanyId companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (!permissionChecker.canManageDiscountPolicies(actorId, companyId)) {
            logger.warn(
                    "Permission denied for discount management. actorId={}, companyId={}",
                    actorId,
                    companyId
            );

            throw new SecurityException(
                    "actor is not allowed to manage policies for company: " + companyId
            );
        }
    }

    private void requireCompanyOwnsDiscountPolicy(DiscountPolicyId policyId, CompanyId companyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        DiscountPolicy policy = getByIdOrThrow(policyId);

        if (!policy.companyId().equals(companyId)) {
            logger.warn(
                    "Cannot modify discount policy of another company. requestedCompanyId={}, policyCompanyId={}, policyId={}",
                    companyId,
                    policy.companyId(),
                    policyId
            );

            throw new SecurityException("Cannot modify discount policies of other companies");
        }
    }

    private void requireCompanyOwnsEvent(CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");


        if (!eventOwnershipChecker.isEventByCompany(eventId, companyId)) {
            logger.warn(
                    "Event is not owned by company!. eventId={}, companyId={}",
                    eventId,
                    companyId
            );

            throw new SecurityException(
                    "actor is not allowed to manage policies for company: " + companyId
            );
        }
    }

    @Override
    public PurchaseContext createPurchaseContext(EventId eventId, BuyerReference buyerRef, List<OrderItem> items, String discountCode) {
        CompanyId companyId = eventOwnershipChecker.companyOfEvent(eventId);
        List<ZoneId> zonesOfEachTicket = eventOwnershipChecker.getZonesOfTicketsForEvent(eventId,items);
        LocalDate buyerBirthday = memberInfoPort.getMemberBirthdate(new MemberId(buyerRef.memberId()));
        return new PurchaseContext(eventId, companyId, zonesOfEachTicket, buyerBirthday, discountCode);
    }

    

}