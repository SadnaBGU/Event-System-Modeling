package com.eventsystem.application.event;

import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.policy.PurchasePolicy;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.application.policy.DiscountPolicyService;
import com.eventsystem.application.policy.IPurchasePolicyRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;



@Service
public class EventPurchaseSupportService implements IEventQueryPort{

    private final IEventRepository eventRepository;
    private final IZoneRepository zoneRepository;
    private final IPurchasePolicyRepository ppolicyRepository;
    private static final Logger logger = LoggerFactory.getLogger(EventPurchaseSupportService.class);


    public EventPurchaseSupportService( IEventRepository eventRepository, IZoneRepository zoneRepository, IPurchasePolicyRepository ppolicyRepository) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository must not be null");
        this.zoneRepository = Objects.requireNonNull(zoneRepository, "zoneRepository must not be null" );
        this.ppolicyRepository = Objects.requireNonNull( ppolicyRepository, "purchasePolicyRepository must not be null");
    }

    public boolean isPurchasable(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        logger.debug("Checking if event is purchasable. eventId={}", eventId.value());
        Event event = loadEvent(eventId);

        return event.isPurchasable();
    }

    public void requirePurchasable(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Event event = loadEvent(eventId);

        event.requirePurchasable();
    }

    public boolean areAllZonesFull(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        logger.debug("Checking whether all zones are full. eventId={}", eventId.value());
        List<Zone> zones = zoneRepository.findByEventId(eventId);

        return !zones.isEmpty()
                && zones.stream().allMatch(zone -> zone.getAvailableCount() == 0);
    }

    public void markSoldOutIfAllZonesFull(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Event event = loadEvent(eventId);

        if (!event.isPublished()) {
            logger.warn("Skipping sold-out update because event is not published. eventId={}", eventId.value());
            return;
            
        }

        List<Zone> zones = zoneRepository.findByEventId(eventId);

        boolean allZonesFull = !zones.isEmpty()
                && zones.stream().allMatch(zone -> zone.getAvailableCount() == 0);

        if (allZonesFull) {
            event.markSoldOut();
            eventRepository.save(event);
            logger.info("Event marked as sold out. eventId={}", eventId.value());
        }
    }

    public Set<ZoneId> getZonesOfEvent(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Event event = loadEvent(eventId);
        return event.zoneIds();
    }


    public Event getEventForSnapshot(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        return loadEvent(eventId);
    }

    public void requireZoneBelongsToEvent(EventId eventId, ZoneId zoneId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");

        Event event = loadEvent(eventId);
        if (!event.isZoneInEvent(zoneId))
        {
            logger.warn("Zone does not belong to event. eventId={}, zoneId={}",eventId.value(), zoneId.value());
        }
        event.requireZoneBelongsToEvent(zoneId);
    }

    public PolicyValidationResult validateContextAgainstEvent(EventId eventId, PurchaseContext context) {
        Event event = loadEvent(eventId);

        if (!event.isPurchasable()) {
            return PolicyValidationResult.failure("Event tickets are not purchasable");
        }

        for (ZoneId zoneId : context.zonesOfEachEventTicket()) {
            if (!event.isZoneInEvent(zoneId)) {
                return PolicyValidationResult.failure("Tickets for an invalid zone for event in ordered items");
            }
        }

        return PolicyValidationResult.success();
    }

    private PolicyValidationResult evalEventPurchaseBeforePolicyValidation(String eventId, BuyerReference buyer, List<OrderItem> items) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(buyer, "buyer must not be null");
        Objects.requireNonNull(items, "items must not be null");

        if (items.isEmpty()) {
            return PolicyValidationResult.failure("No items to purchase");
        }

        Event event = loadEvent(new EventId(eventId));

        if (!event.isPurchasable()) {
            return PolicyValidationResult.failure("Event tickets are not purchasable");
        }

        for (OrderItem item : items) {
            if (item == null) {
                return PolicyValidationResult.failure("Invalid item in ordered items");
            }

            if (item.getZoneId() == null || item.getZoneId().isBlank()) {
                return PolicyValidationResult.failure("Invalid zone id for item in ordered items");
            }
            if (item.getQuantity() <= 0) {
                return PolicyValidationResult.failure("Invalid item quantity in ordered items");
            }

            if (item.getUnitPrice() == null) {
                return PolicyValidationResult.failure("Invalid item price for item in ordered items");
            }

            ZoneId zoneId = new ZoneId(item.getZoneId());

            if (!event.isZoneInEvent(zoneId)) {
                return PolicyValidationResult.failure("Tickets for an invalid zone for event in ordered items");
            }
        }
        return PolicyValidationResult.success();
    }

    //TODO - Uses placeholder birthday, replace! see func with birthday as parameter
    public PurchaseContext fromPurchaseInfo(String eventId, BuyerReference buyer, List<OrderItem> items) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(buyer, "buyer must not be null");
        Objects.requireNonNull(items, "items must not be null");
        EventId eid  = new EventId(eventId);
        Event event = loadEvent(eid);
        ArrayList<ZoneId> ticketZoneList = new ArrayList<>();
        for (OrderItem item : items) {
            ZoneId zoneIdOfitem = new ZoneId(item.getZoneId());
            if (event.isZoneInEvent(zoneIdOfitem)) {
                for (int i = 0; i < item.getQuantity(); i++) {
                    ticketZoneList.add(zoneIdOfitem);
                }
            }
        }
        CompanyId cid = event.companyId();

        //TODO- get buyer birthday date and replace the placeholder!
        return new PurchaseContext(eid, cid,ticketZoneList ,placeholderBuyerBirthDate(), normalizeDiscountCode(null));
    }
    //TODO - Uses placeholder birthday, replace! see func with birthday as parameter
    public PurchaseContext fromPurchaseInfo(String eventId, BuyerReference buyer, List<OrderItem> items,String discountCode) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(buyer, "buyer must not be null");
        Objects.requireNonNull(items, "items must not be null");
        EventId eid  = new EventId(eventId);
        Event event = loadEvent(eid);
        ArrayList<ZoneId> ticketZoneList = new ArrayList<>();
        for (OrderItem item : items) {
            ZoneId zoneIdOfitem = new ZoneId(item.getZoneId());
            if (event.isZoneInEvent(zoneIdOfitem)) {
                for (int i = 0; i < item.getQuantity(); i++) {
                    ticketZoneList.add(zoneIdOfitem);
                }
            }
        }
        CompanyId cid = event.companyId();

        //TODO- get buyer birthday date and replace the placeholder!
        return new PurchaseContext(eid, cid,ticketZoneList ,placeholderBuyerBirthDate(), normalizeDiscountCode(discountCode));
    }

    public PolicyValidationResult evaluateEventPurchasePolicy(EventId eventId, PurchaseContext context) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(context, "context must not be null");
        logger.debug("evaluating purchase policy for event. eventId={}", eventId.value());
        PurchasePolicy policy = ppolicyRepository.findByEventId(eventId).orElse(PurchasePolicy.AllowAll());
        PolicyValidationResult evalResult = policy.evaluate(context);
        if(evalResult.isSuccess()) {
            logger.info("Purchase policy validation passed. eventId={}",eventId.value());
        }
        else {
            logger.warn("Purchase policy validation failed. eventId={}, reason={}",
                    eventId.value(), evalResult.reason());
        }
        return evalResult;
    }

    @Override //TODO - Uses placeholder birthday, replace! see func with birthday as parameter
    public boolean validatePurchasePolicy(String eventId, BuyerReference buyer, List<OrderItem> items) {
        PolicyValidationResult res = evalEventPurchaseBeforePolicyValidation(eventId, buyer, items);
        if (!res.isSuccess()) {
            return false;
        }
        EventId eid = new EventId(eventId);
        PurchaseContext context = fromPurchaseInfo(eventId, buyer, items);
        res = evaluateEventPurchasePolicy(eid, context);
        if (!res.isSuccess()) {
            return false;
        }
        res = evaluateEventPurchasePolicy(eid, context);
        return res.isSuccess();
    }

    public PurchaseContext fromPurchaseInfo( String eventId, BuyerReference buyer, 
                                             List<OrderItem> items,LocalDate buyerBirthDate) {
        return fromPurchaseInfo(eventId, buyer, items, buyerBirthDate, null);
    }

    public PurchaseContext fromPurchaseInfo( String eventId, BuyerReference buyer, List<OrderItem> items,
                                             LocalDate buyerBirthDate, String discountCode ) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(buyer, "buyer must not be null");
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(buyerBirthDate, "buyerBirthDate must not be null");

        EventId eid = new EventId(eventId);
        Event event = loadEvent(eid);

        List<ZoneId> ticketZoneList = new ArrayList<>();

        for (OrderItem item : items) {
            Objects.requireNonNull(item, "order item must not be null");

            ZoneId zoneId = new ZoneId(item.getZoneId());

            if (!event.isZoneInEvent(zoneId)) {
                logger.warn("Cannot build purchase context: zone does not belong to event. eventId={}, zoneId={}",
                        eventId, zoneId.value());
                throw new PolicyException("Zone does not belong to event: " + zoneId.value());
            }

            for (int i = 0; i < item.getQuantity(); i++) {
                ticketZoneList.add(zoneId);
            }
        }

        CompanyId cid = event.companyId();


        return new PurchaseContext(eid, cid, ticketZoneList, buyerBirthDate, normalizeDiscountCode(discountCode));
    }   

    public boolean validatePurchasePolicy(String eventId, BuyerReference buyer, List<OrderItem> items, LocalDate buyerBirthDate) {
        PolicyValidationResult res = evalEventPurchaseBeforePolicyValidation(eventId, buyer, items);
        if (!res.isSuccess()) {
            return false;
        }
        EventId eid = new EventId(eventId);
        PurchaseContext context = fromPurchaseInfo(eventId, buyer, items, buyerBirthDate);
        res = evaluateEventPurchasePolicy(eid, context);
        if (!res.isSuccess()) {
            return false;
        }
        res = evaluateEventPurchasePolicy(eid, context);
        return res.isSuccess();
    }

    public PolicyValidationResult getValidatePurchasePolicyResults(String eventId, BuyerReference buyer, List<OrderItem> items, LocalDate buyerBirthDate) {
        PolicyValidationResult res = evalEventPurchaseBeforePolicyValidation(eventId, buyer, items);
        if (!res.isSuccess()) {
            return res;
        }
        EventId eid = new EventId(eventId);
        PurchaseContext context = fromPurchaseInfo(eventId, buyer, items, buyerBirthDate);
        res = evaluateEventPurchasePolicy(eid, context);
        if (!res.isSuccess()) {
            return res;
        }
        res = evaluateEventPurchasePolicy(eid, context);
        return res;
    }

    /**
     * TODO: replace!
     * <p>This is the old event-level discount mechanism.
     * Once checkout saga integrates with DiscountPolicyService, this method should probably delegate
     * to the new discount policy flow or be replaced in EventQueryPort.
     * @see DiscountPolicyService
     */
    @Override
    public DiscountSnapshot applyDiscount(String eventId, String discountCode, Money baseTotal) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(baseTotal, "baseTotal must not be null");

        Event event = loadEvent(new EventId(eventId));

        if (event.isValidDiscountCode(discountCode))
        {
            DiscountSnapshot snapshot = event.getDiscountSnapshot(discountCode, baseTotal);
            logger.info("Discount applied. eventId={}, discountCode={}, discountAmount={}",
                eventId, discountCode, snapshot.discountAmount());
            return snapshot;
        }
        logger.info("NO Discount applied. eventId={}, discountCode={}", eventId, discountCode);
        return new DiscountSnapshot("NO_DISCOUNT", Money.of(BigDecimal.ZERO, baseTotal.currency())
        );
    }

    @Override
    public EventSnapshot getEventSnapshot(String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Event event = loadEvent(new EventId(eventId));
        //TODO - Needs to replace CompanyId with name
        return new EventSnapshot( event.id().value(), event.details().name(), event.companyId().toString(),
                            event.details().dates().get(0).toLocalDate(), event.details().location());
    }

    private Event loadEvent(EventId eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    logger.warn("Event not found. eventId={}", eventId.value());
                    return new IllegalArgumentException("event not found: " + eventId.value());
                });
    }

    private String normalizeDiscountCode(String discountCode) {
        return discountCode == null || discountCode.isBlank()
                ? null
                : discountCode.trim();
    }

    private LocalDate placeholderBuyerBirthDate() {
        /*
         * TODO:
         * Replace once checkout saga passes buyer birth date or member profile data.
         * This placeholder prevents null context and keeps current EventQueryPort working.
         *
         * Chosen as 18 years old so MinAgePolicy(18) can pass during placeholder integration.
         * This must not be treated as real production logic.
         */
        return LocalDate.now().minusYears(18);
    }
}