package com.eventsystem.application.acceptance;

import com.eventsystem.application.TestPurchaseContexts;

import com.eventsystem.application.company.ProductionCompanyService;
import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.event.IEventQueryPort;
import com.eventsystem.application.member.IMemberInformationPort;
import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.application.order.*;
import com.eventsystem.application.policy.DiscountApplicationService;
import com.eventsystem.application.policy.IDiscountApplicationPort;
import com.eventsystem.application.policy.IPurchasePolicyValidationPort;
import com.eventsystem.application.policy.PolicyManagementService;
import com.eventsystem.application.policy.PurchasePolicyValidationService;
import com.eventsystem.application.policy.policybuilder.PolicyCommandAssembler;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.IProductionCompanyRepository;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.SalesMethod;
import com.eventsystem.domain.lottery.ILotteryRepository;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;
import com.eventsystem.domain.order.*;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.domain.policy.discount.DiscountSummary;
import com.eventsystem.domain.policy.discount.IDiscountPolicyRepository;
import com.eventsystem.domain.policy.purchase.IPurchasePolicyRepository;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.purchaserecord.*;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Test fixture for application-level acceptance tests.
 *
 * Uses real application services and real domain objects, but fake in-memory
 * repositories and fake external ports. That lets the test execute a full
 * use-case scenario without Spring, DB, REST, or WSEP.
 */
class ApplicationAcceptanceFixture {

    // ---------------------------------------------------------------------
    // First-batch checkout/order fakes
    // ---------------------------------------------------------------------

    final FakeActiveOrderRepository orders = new FakeActiveOrderRepository();
    final FakeZoneRepository zones = new FakeZoneRepository();
    final FakePurchaseRecordRepository purchaseRecords = new FakePurchaseRecordRepository();
    final FakeLotteryRepository lotteries = new FakeLotteryRepository();

    final FakePaymentGatewayPort payment = new FakePaymentGatewayPort();
    final FakeTicketIssuancePort ticketing = new FakeTicketIssuancePort();
    final FakeNotificationPort notifications = new FakeNotificationPort();

    /**
     * Simple stub ports used by the first UC09 tests.
     * These are useful when we want to force policy/discount behavior directly.
     */
    final FakePurchasePolicyValidationPort purchasePolicies = new FakePurchasePolicyValidationPort();
    final FakeDiscountApplicationPort discounts = new FakeDiscountApplicationPort();

    final FakeEventQueryPort events = new FakeEventQueryPort();

    final OrderService orderService = new OrderService(
            orders,
            zones,
            new OrderFactory(),
            lotteries);

    final CheckoutSaga checkoutSaga = new CheckoutSaga(
            orders,
            purchaseRecords,
            payment,
            ticketing,
            notifications,
            zones,
            purchasePolicies,
            discounts,
            events);

    // ---------------------------------------------------------------------
    // Added for UC16 policy tests and UC17/20 role-permission tests
    // ---------------------------------------------------------------------

    final FakeMemberRepository members = new FakeMemberRepository();
    final FakeProductionCompanyRepository companies = new FakeProductionCompanyRepository();
    final FakeEventManagementPort eventManagement = new FakeEventManagementPort();
    final FakeMemberInformationPort memberInfo = new FakeMemberInformationPort();

    final FakePurchasePolicyRepository realPurchasePolicies = new FakePurchasePolicyRepository();
    final FakeDiscountPolicyRepository realDiscountPolicies = new FakeDiscountPolicyRepository();

    final ProductionCompanyService companyService = new ProductionCompanyService(
            companies,
            members);

    final PolicyCommandAssembler policyCommandAssembler = new PolicyCommandAssembler();

    final PolicyManagementService policyManagementService = new PolicyManagementService(
            realPurchasePolicies,
            realDiscountPolicies,
            companyService,
            eventManagement,
            policyCommandAssembler);

    final PurchasePolicyValidationService realPurchasePolicyValidationService = new PurchasePolicyValidationService(
            realPurchasePolicies,
            eventManagement,
            memberInfo);

    final DiscountApplicationService realDiscountApplicationService = new DiscountApplicationService(
            realDiscountPolicies,
            eventManagement,
            memberInfo);

    /**
     * Checkout saga that uses the real policy/discount application services.
     * Use this in UC16 tests.
     */
    final CheckoutSaga checkoutSagaWithRealPolicies = new CheckoutSaga(
            orders,
            purchaseRecords,
            payment,
            ticketing,
            notifications,
            zones,
            realPurchasePolicyValidationService,
            realDiscountApplicationService,
            events);

    // ---------------------------------------------------------------------
    // Shared helper methods
    // ---------------------------------------------------------------------

    BuyerReference memberBuyer(String memberId) {
        return new BuyerReference(BuyerType.MEMBER, "session-" + memberId, memberId);
    }

    EventId eventId(String value) {
        return new EventId(value);
    }

    ZoneId zoneId(String value) {
        return new ZoneId(value);
    }

    SeatId seatId(String value) {
        return new SeatId(value);
    }

    MemberId memberId(String value) {
        return new MemberId(value);
    }

    CompanyId companyId(String value) {
        return new CompanyId(value);
    }

    Money usd(String amount) {
        return Money.of(new BigDecimal(amount), "USD");
    }

    void saveMember(String memberId) {
        MemberId id = memberId(memberId);
        members.save(new Member(id));
        memberInfo.birthdates.put(id, LocalDate.now().minusYears(25));
        memberInfo.statuses.put(id, MemberStatus.ACTIVE);
    }

    void createMemberIfMissing(String memberId) {
        MemberId id = memberId(memberId);
        if (members.findById(id).isEmpty()) {
            saveMember(memberId);
        }
    }

    CompanyId createCompanyWithFounder(String founderId) {
        saveMember(founderId);
        return companyService.createCompany(
                memberId(founderId),
                "Company " + founderId,
                "Acceptance test company",
                5.0);
    }

    Zone createSeatedZone(String eventId, String zoneId, String seatId, String price) {
        return createSeatedZoneForCompany(
                eventId,
                zoneId,
                seatId,
                price,
                new CompanyId("company-1"));
    }

    Zone createSeatedZoneForCompany(
            String eventId,
            String zoneId,
            String seatId,
            String price,
            CompanyId companyId) {
        Zone zone = Zone.createSeated(
                zoneId(zoneId),
                eventId(eventId),
                "VIP",
                usd(price),
                List.of(new Row("A", List.of(new Seat(seatId(seatId), "A", 1)))));

        zones.save(zone);

        String companyName = companies.findById(companyId)
                .map(company -> company.companyDetails().name())
                .orElse("Company");

        events.registerEvent(
                eventId,
                "Concert",
                companyName,
                LocalDate.now().plusDays(30),
                "Venue",
                companyId);

        eventManagement.registerEvent(companyId, eventId(eventId));
        eventManagement.registerZone(eventId(eventId), zoneId(zoneId));

        return zone;
    }

    ActiveOrderDTO createStrictOrder(BuyerReference buyer, String eventId) {
        return orderService.createNewOrderStrict(buyer, eventId);
    }

    ActiveOrderDTO createOrder(BuyerReference buyer, String eventId) {
        return orderService.createOrGetActiveOrder(buyer, eventId, Optional.empty());
    }

    ActiveOrder createExpiredOrderWithReservedSeat(
            BuyerReference buyer,
            String eventId,
            String zoneId,
            String seatId,
            String price) {
        ActiveOrder order = new ActiveOrder(
                "expired-order-" + UUID.randomUUID(),
                buyer,
                eventId,
                Instant.now().plus(10, ChronoUnit.MINUTES));

        order.addItem(new OrderItem(zoneId, seatId, 1, usd(price)));
        forceReservationExpiry(order, Instant.now().minus(1, ChronoUnit.MINUTES));
        orders.save(order);

        return order;
    }

    private void forceReservationExpiry(ActiveOrder order, Instant expiry) {
        try {
            var field = ActiveOrder.class.getDeclaredField("reservationExpiry");
            field.setAccessible(true);
            field.set(order, expiry);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create expired active order fixture", e);
        }
    }

    void reserveSeat(String orderId, String zoneId, String seatId) {
        orderService.addItemToOrder(orderId, zoneId, seatId, 1);
    }

    ActiveOrder order(String orderId) {
        return orders.findById(orderId).orElseThrow();
    }

    Zone zone(String zoneId) {
        return zones.findById(zoneId(zoneId)).orElseThrow();
    }

    Zone createStandingZone(
            String eventId,
            String zoneId,
            String zoneName,
            String price,
            int capacity) {
        Zone zone = Zone.createStanding(
                zoneId(zoneId),
                eventId(eventId),
                zoneName,
                usd(price),
                capacity);

        zones.save(zone);

        events.registerEvent(
                eventId,
                "Concert",
                "ProdCo",
                LocalDate.now().plusDays(30),
                "Venue",
                new CompanyId("company-1"));

        return zone;
    }

    Zone createStandingZoneForCompany(
            String eventId,
            String zoneId,
            String zoneName,
            String price,
            int capacity,
            CompanyId companyId) {
        Zone zone = Zone.createStanding(
                zoneId(zoneId),
                eventId(eventId),
                zoneName,
                usd(price),
                capacity);

        zones.save(zone);

        String companyName = companies.findById(companyId)
                .map(company -> company.companyDetails().name())
                .orElse("Company");

        events.registerEvent(
                eventId,
                "Concert",
                companyName,
                LocalDate.now().plusDays(30),
                "Venue",
                companyId);

        eventManagement.registerEvent(companyId, eventId(eventId));
        eventManagement.registerZone(eventId(eventId), zoneId(zoneId));

        return zone;
    }

    void reserveStanding(String orderId, String zoneId, int quantity) {
        orderService.addItemToOrder(orderId, zoneId, null, quantity);
    }

    // ---------------------------------------------------------------------
    // Fake repositories and ports
    // ---------------------------------------------------------------------

    static final class FakeActiveOrderRepository implements IActiveOrderRepository {
        private final Map<String, ActiveOrder> byId = new LinkedHashMap<>();

        @Override
        public Optional<ActiveOrder> findById(String orderId) {
            return Optional.ofNullable(byId.get(orderId));
        }

        @Override
        public Optional<ActiveOrder> findByBuyerAndEvent(BuyerReference buyer, String eventId) {
            return byId.values().stream()
                    .filter(order -> Objects.equals(order.getBuyerRef(), buyer))
                    .filter(order -> Objects.equals(order.getEventId(), eventId))
                    .filter(order -> order.getStatus() == OrderStatus.ACTIVE)
                    .findFirst();
        }

        @Override
        public Optional<List<ActiveOrder>> findExpired() {
            return Optional.of(byId.values().stream()
                    .filter(ActiveOrder::isExpired)
                    .collect(Collectors.toList()));
        }

        @Override
        public void save(ActiveOrder order) {
            byId.put(order.getOrderId(), order);
        }

        @Override
        public void delete(String orderId) {
            byId.remove(orderId);
        }
    }

    static final class FakeZoneRepository implements IZoneRepository {
        private final Map<ZoneId, Zone> byId = new LinkedHashMap<>();
        private final Map<ZoneId, ReentrantLock> locks = new ConcurrentHashMap<>();

        @Override
        public Optional<Zone> findById(ZoneId zoneId) {
            return Optional.ofNullable(byId.get(zoneId));
        }

        @Override
        public List<Zone> findByEventId(EventId eventId) {
            return byId.values().stream()
                    .filter(zone -> zone.eventId().equals(eventId))
                    .toList();
        }

        @Override
        public void save(Zone zone) {
            byId.put(zone.zoneId(), zone);
        }

        @Override
        public void withLock(ZoneId zoneId, Runnable action) {
            ReentrantLock lock = locks.computeIfAbsent(zoneId, ignored -> new ReentrantLock());
            lock.lock();
            try {
                action.run();
            } finally {
                lock.unlock();
            }
        }
    }

    static final class FakePurchaseRecordRepository implements IPurchaseRecordRepository {
        private final List<PurchaseRecord> records = new ArrayList<>();

        @Override
        public void append(PurchaseRecord record) {
            records.add(record);
        }

        @Override
        public Optional<PurchaseRecord> findById(String recordId) {
            return records.stream()
                    .filter(record -> record.getRecordId().equals(recordId))
                    .findFirst();
        }

        @Override
        public List<PurchaseRecord> findByBuyer(String buyerId) {
            return records.stream()
                    .filter(record -> record.getBuyerId().equals(buyerId))
                    .toList();
        }

        @Override
        public List<PurchaseRecord> findByEvent(String eventId) {
            return records.stream()
                    .filter(record -> record.getEventSnapshot().eventId().equals(eventId))
                    .toList();
        }

        @Override
        public List<PurchaseRecord> findAll() {
            return List.copyOf(records);
        }
    }

    static final class FakeLotteryRepository implements ILotteryRepository {
        private final Map<LotteryId, Lottery> byId = new LinkedHashMap<>();

        @Override
        public Optional<Lottery> findById(LotteryId lotteryId) {
            return Optional.ofNullable(byId.get(lotteryId));
        }

        @Override
        public Optional<Lottery> findByEventId(EventId eventId) {
            return byId.values().stream()
                    .filter(lottery -> lottery.getEventId().equals(eventId))
                    .findFirst();
        }

        @Override
        public void save(Lottery lottery) {
            byId.put(lottery.getLotteryId(), lottery);
        }
    }

    static final class FakePaymentGatewayPort implements IPaymentGatewayPort {
        enum Mode {
            SUCCESS, DECLINED, THROW_TIMEOUT
        }

        Mode mode = Mode.SUCCESS;
        int charges;
        int refunds;
        String lastTransactionId = "TXN-1";
        Money lastChargedAmount;
        String lastRefundTransactionId;
        Money lastRefundAmount;
        String lastRefundReason;

        @Override
        public PaymentResult charge(String orderId, Money amount, BuyerReference buyer, String paymentDetailsToken) {
            charges++;
            lastChargedAmount = amount;

            if (mode == Mode.THROW_TIMEOUT) {
                throw new RuntimeException("payment gateway timeout");
            }

            if (mode == Mode.DECLINED) {
                return PaymentResult.failed("card declined");
            }

            return PaymentResult.successful(lastTransactionId);
        }

        @Override
        public RefundResult refund(String transactionId, Money amount, String reason) {
            refunds++;
            lastRefundTransactionId = transactionId;
            lastRefundAmount = amount;
            lastRefundReason = reason;
            return new RefundResult(true, null);
        }
    }

    static final class FakeTicketIssuancePort implements ITicketIssuancePort {
        enum Mode {
            SUCCESS, FAILED, THROW_EXCEPTION
        }

        Mode mode = Mode.SUCCESS;
        int attempts;
        List<OrderItem> lastIssuedItems = List.of();

        @Override
        public IssuanceResult issueTickets(
                String eventId,
                String activeOrderId,
                List<OrderItem> items,
                BuyerReference buyer) {
            attempts++;
            lastIssuedItems = List.copyOf(items);

            if (mode == Mode.THROW_EXCEPTION) {
                throw new RuntimeException("ticket supply disconnected");
            }

            if (mode == Mode.FAILED) {
                return IssuanceResult.failed("ticket supply failed");
            }

            return IssuanceResult.successful(List.of("TIX-1"));
        }
    }

    static final class FakeNotificationPort implements INotificationPort {
        final List<String> purchaseSuccesses = new ArrayList<>();
        final List<String> purchaseFailures = new ArrayList<>();
        final List<String> queueTurns = new ArrayList<>();
        final List<String> soldOuts = new ArrayList<>();
        final List<String> lotteryWins = new ArrayList<>();

        @Override
        public void sendPurchaseSuccess(BuyerReference buyer, String receiptId) {
            purchaseSuccesses.add(buyer.memberId() + ":" + receiptId);
        }

        @Override
        public void sendPurchaseFailure(BuyerReference buyer, String reason) {
            purchaseFailures.add(buyer.memberId() + ":" + reason);
        }

        @Override
        public void sendQueueTurnArrived(BuyerReference buyer, String eventId) {
            queueTurns.add(buyer.memberId() + ":" + eventId);
        }

        @Override
        public void sendEventSoldOut(BuyerReference buyer, String eventId) {
            soldOuts.add(buyer.memberId() + ":" + eventId);
        }

        @Override
        public void sendLotteryWon(BuyerReference buyer, String eventId, String permissionCode) {
            lotteryWins.add(buyer.memberId() + ":" + eventId);
        }
    }

    static final class FakePurchasePolicyValidationPort implements IPurchasePolicyValidationPort {
        PolicyValidationResult result = PolicyValidationResult.success();
        int evaluations;

        @Override
        public void requirePurchasePolicyFor(PurchaseContext context) {
            PolicyValidationResult evaluation = evaluatePurchasePolicyFor(context);
            if (!evaluation.isSuccess()) {
                throw new IllegalStateException(evaluation.reason());
            }
        }

        @Override
        public boolean validatePurchasePolicyFor(PurchaseContext context) {
            return evaluatePurchasePolicyFor(context).isSuccess();
        }

        @Override
        public PolicyValidationResult evaluatePurchasePolicyFor(PurchaseContext context) {
            evaluations++;
            return result;
        }

        @Override
        public PurchaseContext createPurchaseContext(EventId eventId, BuyerReference buyerRef, List<OrderItem> items) {
            return TestPurchaseContexts.contextFromOrderItems(
                    eventId,
                    new CompanyId("company-1"),
                    items,
                    null);
        }

    }

    static final class FakeDiscountApplicationPort implements IDiscountApplicationPort {
        DiscountSnapshot snapshot = new DiscountSnapshot("NONE", Money.of(BigDecimal.ZERO, "USD"));
        int snapshotsGenerated;

        @Override
        public boolean doesDiscountApplyFor(PurchaseContext context) {
            return snapshot.discountAmount().amount().compareTo(BigDecimal.ZERO) > 0;
        }

        @Override
        public DiscountSummary calculateDiscountSummary(PurchaseContext context, Money baseCost) {
            return DiscountSummary.noDiscountSummary();
        }

        @Override
        public DiscountSnapshot generateDiscountSnapshot(PurchaseContext context, Money baseTotal) {
            snapshotsGenerated++;
            return snapshot;
        }

        @Override
        public List<EventId> getEventIdsWithActiveVisibleDiscounts() {
            return List.of();
        }

        @Override
        public PurchaseContext createPurchaseContext(
                EventId eventId,
                BuyerReference buyerRef,
                List<OrderItem> items,
                String discountCode) {
            return TestPurchaseContexts.contextFromOrderItems(
                    eventId,
                    new CompanyId("company-1"),
                    items,
                    discountCode);
        }

        @Override
        public DiscountSnapshot discountSnapshotFromSummary(DiscountSummary summary, Money baseTotal) {
            return DiscountPolicy.discountSnapshotFromSummary(summary, baseTotal);
        }

    }

    static final class FakeEventQueryPort implements IEventQueryPort {
        private final Map<String, EventSnapshot> snapshots = new LinkedHashMap<>();
        private final Map<EventId, CompanyId> companyByEvent = new LinkedHashMap<>();

        void registerEvent(
                String eventId,
                String name,
                String companyName,
                LocalDate date,
                String location,
                CompanyId companyId) {
            snapshots.put(eventId, new EventSnapshot(eventId, name, companyName, date, location));
            companyByEvent.put(new EventId(eventId), companyId);
        }

        @Override
        public EventSnapshot getEventSnapshot(String eventId) {
            return snapshots.getOrDefault(
                    eventId,
                    new EventSnapshot(
                            eventId,
                            "Event " + eventId,
                            "Company",
                            LocalDate.now().plusDays(30),
                            "Venue"));
        }

        @Override
        public CompanyId companyOfEvent(EventId eventId) {
            return companyByEvent.getOrDefault(eventId, new CompanyId("company-1"));
        }
    }

    // ---------------------------------------------------------------------
    // New fakes for policy and role-permission acceptance tests
    // ---------------------------------------------------------------------

    static final class FakeMemberRepository implements IMemberRepository {
        private final Map<MemberId, Member> byId = new LinkedHashMap<>();

        @Override
        public Optional<Member> findById(MemberId memberId) {
            return Optional.ofNullable(byId.get(memberId));
        }

        @Override
        public Optional<Member> findByUsername(String username) {
            return byId.values().stream()
                    .filter(member -> Objects.equals(member.getUsername(), username))
                    .findFirst();
        }

        @Override
        public Collection<Member> findAll() {
            return List.copyOf(byId.values());
        }

        @Override
        public void save(Member member) {
            byId.put(member.getMemberId(), member);
        }
    }

    static final class FakeProductionCompanyRepository implements IProductionCompanyRepository {
        private final Map<CompanyId, ProductionCompany> byId = new LinkedHashMap<>();

        @Override
        public Optional<ProductionCompany> findById(CompanyId companyId) {
            return Optional.ofNullable(byId.get(companyId));
        }

        @Override
        public Optional<ProductionCompany> findByName(String companyName) {
            return byId.values().stream()
                    .filter(company -> Objects.equals(company.companyDetails().name(), companyName))
                    .findFirst();
        }

        @Override
        public void save(ProductionCompany productionCompany) {
            byId.put(productionCompany.companyId(), productionCompany);
        }

        @Override
        public boolean hasPermission(MemberId memberId, CompanyId companyId, Permission permission) {
            return findById(companyId)
                    .map(company -> company.hasPermission(memberId, permission))
                    .orElse(false);
        }

        @Override
        public List<ProductionCompany> findAll() {
            return List.copyOf(byId.values());
        }
    }

    static final class FakeMemberInformationPort implements IMemberInformationPort {
        final Map<MemberId, LocalDate> birthdates = new LinkedHashMap<>();
        final Map<MemberId, MemberStatus> statuses = new LinkedHashMap<>();

        @Override
        public LocalDate getMemberBirthdate(MemberId memberId) {
            return birthdates.getOrDefault(memberId, LocalDate.now().minusYears(25));
        }

        @Override
        public MemberStatus getMemberStatus(MemberId memberId) {
            return statuses.getOrDefault(memberId, MemberStatus.ACTIVE);
        }
    }

    static final class FakeEventManagementPort implements IEventManagementPort {
        private final Map<EventId, CompanyId> companyByEvent = new LinkedHashMap<>();
        private final Map<CompanyId, List<EventId>> eventsByCompany = new LinkedHashMap<>();
        private final Map<ZoneId, EventId> eventByZone = new LinkedHashMap<>();

        void registerEvent(CompanyId companyId, EventId eventId) {
            companyByEvent.put(eventId, companyId);
            eventsByCompany.computeIfAbsent(companyId, ignored -> new ArrayList<>()).add(eventId);
        }

        void registerZone(EventId eventId, ZoneId zoneId) {
            eventByZone.put(zoneId, eventId);
        }

        @Override
        public boolean isEventByCompany(EventId eventId, CompanyId companyId) {
            return companyId.equals(companyByEvent.get(eventId));
        }

        @Override
        public List<EventId> allEventsOfCompany(CompanyId companyId) {
            return List.copyOf(eventsByCompany.getOrDefault(companyId, List.of()));
        }

        @Override
        public void setSalesMethod(MemberId actorId, EventId eventId, SalesMethod salesMethod) {
            // No-op for application acceptance tests.
        }

        @Override
        public CompanyId companyOfEvent(EventId eventId) {
            return companyByEvent.getOrDefault(eventId, new CompanyId("company-1"));
        }

        @Override
        public List<ZoneId> getZonesOfTicketsForEvent(EventId eventId, List<OrderItem> items) {
            return items.stream()
                    .map(item -> new ZoneId(item.getZoneId()))
                    .toList();
        }

        @Override
        public boolean isZoneInEvent(EventId eventId, ZoneId zoneId) {
            return eventId.equals(eventByZone.get(zoneId));
        }
    }

    static final class FakePurchasePolicyRepository implements IPurchasePolicyRepository {
        private final Map<PurchasePolicyId, PurchasePolicy> byId = new LinkedHashMap<>();

        @Override
        public Optional<PurchasePolicy> findById(PurchasePolicyId policyId) {
            return Optional.ofNullable(byId.get(policyId));
        }

        @Override
        public List<PurchasePolicy> findByCompanyId(CompanyId companyId) {
            return byId.values().stream()
                    .filter(policy -> policy.companyId().equals(companyId))
                    .toList();
        }

        @Override
        public List<PurchasePolicy> findActiveByCompanyId(CompanyId companyId) {
            return findByCompanyId(companyId).stream()
                    .filter(PurchasePolicy::isActive)
                    .toList();
        }

        @Override
        public List<PurchasePolicy> findApplicableToPurchase(CompanyId companyId, EventId eventId) {
            return findByCompanyId(companyId).stream()
                    .filter(PurchasePolicy::isActive)
                    .filter(policy -> policy.isActiveForEvent(eventId))
                    .toList();
        }

        @Override
        public void save(PurchasePolicy purchasePolicy) {
            byId.put(purchasePolicy.id(), purchasePolicy);
        }

        @Override
        public void deleteById(PurchasePolicyId policyId) {
            byId.remove(policyId);
        }

        @Override
        public boolean existsById(PurchasePolicyId policyId) {
            return byId.containsKey(policyId);
        }

        @Override
        public List<PurchasePolicy> findCompanyOwnedPolicies(CompanyId companyId) {
            return findByCompanyId(companyId).stream()
                    .filter(PurchasePolicy::isCompanyPolicy)
                    .toList();
        }

        @Override
        public List<PurchasePolicy> findEventOwnedPolicy(EventId eventId) {
            return byId.values().stream()
                    .filter(policy -> policy.scope().isListedIn(eventId))
                    .filter(PurchasePolicy::isEventPolicy)
                    .toList();
        }

        @Override
        public List<PurchasePolicy> findByEventId(EventId eventId) {
            return byId.values().stream().filter(p -> p.scope().isListedIn(eventId)).toList();

        }
    }

    static final class FakeDiscountPolicyRepository implements IDiscountPolicyRepository {
        private final Map<DiscountPolicyId, DiscountPolicy> byId = new LinkedHashMap<>();

        @Override
        public Optional<DiscountPolicy> findById(DiscountPolicyId policyId) {
            return Optional.ofNullable(byId.get(policyId));
        }

        @Override
        public List<DiscountPolicy> findByCompanyId(CompanyId companyId) {
            return byId.values().stream()
                    .filter(policy -> policy.companyId().equals(companyId))
                    .toList();
        }

        @Override
        public List<DiscountPolicy> findAllActive() {
            return byId.values().stream()
                    .filter(DiscountPolicy::isActive)
                    .toList();
        }

        @Override
        public List<DiscountPolicy> findActiveWithVisibleDiscounts() {
            return findAllActive().stream()
                    .filter(DiscountPolicy::doesHaveVisibleDiscounts)
                    .toList();
        }

        @Override
        public List<DiscountPolicy> findActiveByCompanyId(CompanyId companyId) {
            return findByCompanyId(companyId).stream()
                    .filter(DiscountPolicy::isActive)
                    .toList();
        }

        @Override
        public List<DiscountPolicy> findApplicableToPurchase(CompanyId companyId, EventId eventId) {
            return findByCompanyId(companyId).stream()
                    .filter(DiscountPolicy::isActive)
                    .filter(policy -> policy.scope().appliesTo(eventId))
                    .toList();
        }

        @Override
        public void save(DiscountPolicy discountPolicy) {
            byId.put(discountPolicy.id(), discountPolicy);
        }

        @Override
        public void deleteById(DiscountPolicyId policyId) {
            byId.remove(policyId);
        }

        @Override
        public boolean existsById(DiscountPolicyId policyId) {
            return byId.containsKey(policyId);
        }

        @Override
        public List<DiscountPolicy> findCompanyOwnedPolicies(CompanyId companyId) {
            return findByCompanyId(companyId).stream()
                    .filter(DiscountPolicy::isCompanyPolicy)
                    .toList();
        }

        @Override
        public List<DiscountPolicy> findEventOwnedPolicy(EventId eventId) {
            return byId.values().stream()
                    .filter(policy -> policy.scope().isListedIn(eventId))
                    .filter(DiscountPolicy::isEventPolicy)
                    .toList();
        }

        @Override
        public List<DiscountPolicy> findByEventId(EventId eventId) {
            return byId.values().stream().filter(p -> p.scope().isListedIn(eventId)).toList();
        }
    }
}