package com.eventsystem.application.policy;

import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.member.IMemberInformationPort;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.domain.policy.discount.DiscountSummary;
import com.eventsystem.domain.policy.discount.IDiscountPolicyRepository;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class DiscountApplicationService implements IDiscountApplicationPort {

    private static final Logger logger = LoggerFactory.getLogger(DiscountApplicationService.class);

    private final IDiscountPolicyRepository discountPolicyRepository;
    private final IEventManagementPort eventOwnershipChecker;
    private final IMemberInformationPort memberInfoPort;

    public DiscountApplicationService(
            IDiscountPolicyRepository discountPolicyRepository,
            IEventManagementPort eventOwnershipChecker,
            IMemberInformationPort memberInfoPort
    ) {
        this.discountPolicyRepository = Objects.requireNonNull(
                discountPolicyRepository,
                "discountPolicyRepository must not be null"
        );
        this.eventOwnershipChecker = Objects.requireNonNull(
                eventOwnershipChecker,
                "eventOwnershipChecker must not be null"
        );
        this.memberInfoPort = Objects.requireNonNull(
                memberInfoPort,
                "memberInfoPort must not be null"
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
                "Finding discount policies applicable to event. eventId={}",
                eventId.value()
        );
        
        return discountPolicyRepository.findApplicableToPurchase(eventOwnershipChecker.companyOfEvent(eventId), eventId);
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

        List<DiscountPolicy> activeDiscountPolicies = discountPolicyRepository.findAllActive();

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

    public boolean existsById(DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        boolean exists = discountPolicyRepository.existsById(policyId);

        logger.debug("Checked discount policy existence. policyId={}, exists={}", policyId, exists);

        return exists;
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

        List<DiscountPolicy> applicablePolicies =
                discountPolicyRepository.findApplicableToPurchase(companyId, eventId);

        if (applicablePolicies.isEmpty()) {
            logger.info(
                    "No applicable discount policies found. companyId={}, eventId={}",
                    companyId,
                    eventId.value()
            );
            return DiscountSummary.noDiscountSummary();
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
                discountPolicyRepository.findApplicableToPurchase(eventOwnershipChecker.companyOfEvent(parsedEventId), parsedEventId);

        if (applicablePolicies.isEmpty()) {
            return new DiscountSnapshot(
                    "No Discount",
                    Money.of(BigDecimal.ZERO, baseTotal.currency())
            );
        }

        DiscountSummary bestSummary = DiscountSummary.noDiscountSummary();

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

    private DiscountSummary calculateBestSummary(
            List<DiscountPolicy> policies,
            PurchaseContext context,
            Money baseCost
    ) {
        DiscountSummary bestSummary = DiscountSummary.noDiscountSummary();

        for (DiscountPolicy policy : policies) {
            DiscountSummary currentSummary = policy.getFullDiscountSummary(context, baseCost);

            if (currentSummary.totalDiscount().compareTo(bestSummary.totalDiscount()) > 0) {
                bestSummary = currentSummary;
            }
        }

        return bestSummary;
    }

    @Override
    public PurchaseContext createPurchaseContext(
            EventId eventId,
            BuyerReference buyerRef,
            List<OrderItem> items,
            String discountCode
    ) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(buyerRef, "buyerRef must not be null");
        Objects.requireNonNull(items, "items must not be null");

        CompanyId companyId = eventOwnershipChecker.companyOfEvent(eventId);
        List<ZoneId> zonesOfEachTicket = eventOwnershipChecker.getZonesOfTicketsForEvent(eventId, items);
        LocalDate buyerBirthday = memberInfoPort.getMemberBirthdate(new MemberId(buyerRef.memberId()));

        return new PurchaseContext(eventId, companyId, zonesOfEachTicket, buyerBirthday, discountCode);
    }
}