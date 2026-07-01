package com.eventsystem.application.order;

import com.eventsystem.application.appexceptions.ActiveOrderHasExpiredException;
import com.eventsystem.application.appexceptions.IssuanceFailedException;
import com.eventsystem.application.appexceptions.OrderNotFoundException;
import com.eventsystem.application.appexceptions.OrderViolatesPolicyException;
import com.eventsystem.application.appexceptions.PaymentFailedException;
import com.eventsystem.application.appexceptions.PriceCalcException;
import com.eventsystem.application.event.IEventQueryPort;
import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.application.policy.IDiscountApplicationPort;
import com.eventsystem.application.policy.IPurchasePolicyValidationPort;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.IActiveOrderRepository;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.policy.discount.DiscountSummary;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.purchaserecord.BuyerSnapshot;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.purchaserecord.IPurchaseRecordRepository;
import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.domain.purchaserecord.PurchasedItem;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.ZoneType;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Transactional
public class CheckoutSaga {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutSaga.class);

    private final IActiveOrderRepository orderRepository;
    private final IPurchaseRecordRepository purchaseRecordRepository;
    private final IPaymentGatewayPort paymentGateway;
    private final ITicketIssuancePort ticketIssuance;
    private final INotificationPort notificationPort;
    private final IZoneRepository zoneRepository;

    private final IPurchasePolicyValidationPort purchasePolicyPort;
    private final IDiscountApplicationPort discountPort;

    private final IEventQueryPort eventQueryPort;
    private final QueueService queueService;

    public CheckoutSaga(IActiveOrderRepository orderRepository,
            IPurchaseRecordRepository purchaseRecordRepository,
            IPaymentGatewayPort paymentGateway,
            ITicketIssuancePort ticketIssuance,
            INotificationPort notificationPort,
            IZoneRepository zoneRepository,
            IPurchasePolicyValidationPort purchasePolicyPort,
            IDiscountApplicationPort discountPort,
            IEventQueryPort eventQueryPort) {
        this(orderRepository,
            purchaseRecordRepository,
            paymentGateway,
            ticketIssuance,
            notificationPort,
            zoneRepository,
            purchasePolicyPort,
            discountPort,
            eventQueryPort,
            null);
        }

        public CheckoutSaga(IActiveOrderRepository orderRepository,
            IPurchaseRecordRepository purchaseRecordRepository,
            IPaymentGatewayPort paymentGateway,
            ITicketIssuancePort ticketIssuance,
            INotificationPort notificationPort,
            IZoneRepository zoneRepository,
            IPurchasePolicyValidationPort purchasePolicyPort,
            IDiscountApplicationPort discountPort,
            IEventQueryPort eventQueryPort,
            QueueService queueService) {
        this.orderRepository = orderRepository;
        this.purchaseRecordRepository = purchaseRecordRepository;
        this.paymentGateway = paymentGateway;
        this.ticketIssuance = ticketIssuance;
        this.notificationPort = notificationPort;
        this.zoneRepository = zoneRepository;
        this.purchasePolicyPort = purchasePolicyPort;
        this.discountPort = discountPort;
        this.eventQueryPort = eventQueryPort;
        this.queueService = queueService;
    }

    /**
     * This method orchestrates the entire checkout process for an active order. It
     * includes:
     * 1. Validating the order and its expiration.
     * 2. Validating purchase policies before discounts (as required by Stream 4
     * instructions).
     * 3. Calculating the total price and applying discounts.
     * 4. Charging the payment through the PaymentGateway.
     * 5. Issuing tickets through the TicketIssuance service (distributed
     * transaction).
     * 6. If any step fails, compensating actions are taken (e.g., refunding
     * payment, unlocking seats).
     * 7. If all steps succeed, a purchase record is created, the order is marked as
     * checked out, and a success notification is sent.
     * 
     * Note: This method assumes that the caller has already verified that the order
     * exists and belongs to the buyer.
     */
    /**
     * REQ: INV-10, ROB-01, UC 9, UAT-26, UAT-29, UAT-30
     *
     * Checkout Saga.
     * Success: payment + ticket issuing succeed, reserved inventory becomes SOLD,
     * purchase record is saved, and the order becomes CHECKED_OUT.
     *
     * Compensation: if payment succeeds but ticket issuing fails, refund is
     * requested,
     * reserved inventory is released, the order becomes CANCELLED, and no purchase
     * record is saved.
     */
    @Transactional(noRollbackFor = IssuanceFailedException.class)
    public CheckoutResult executeCheckout(String orderId, String paymentToken, String discountCode) {
        logger.info("Initiating checkout process for order: {}", orderId);
        // 1. Validate the order and its expiration
        ActiveOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    logger.warn("Checkout failed: Order not found");
                    return new OrderNotFoundException(orderId);
                });

        if (order.isExpired()) {
            logger.warn("Checkout failed: Order reservation timer has expired");
            throw new ActiveOrderHasExpiredException(orderId);
        }

        // 2. Validating purchase policies before discounts
        PurchaseContext context = purchasePolicyPort.createPurchaseContext(new EventId(order.getEventId()),
                order.getBuyerRef(), order.getItems());
        PolicyValidationResult policyValidationResult;
        try {
            policyValidationResult = purchasePolicyPort.evaluatePurchasePolicyFor(context);
        } catch (Exception e) {
            logger.error("Error during purchase policy validation: {}", e.getMessage());
            throw new OrderViolatesPolicyException(
                    "Error validating purchase policy for order " + orderId + ": " + e.getMessage());
        }

        if (!policyValidationResult.isSuccess()) {
            logger.warn("Checkout failed: Order violates purchase policy for event {}", order.getEventId());
            logger.warn("Reason: {}", policyValidationResult.reason());
            throw new OrderViolatesPolicyException(policyValidationResult,"Order" + orderId + " violates purchase policy for event "
                    + order.getEventId() + "Reason:" + policyValidationResult.reason());
        }

        // 3. Calculating the total price and applying discounts

        Money finalAmount;
        DiscountSnapshot discount;
        try {
            Money baseTotal = order.calculateBaseTotal();
            DiscountSummary summ = discountPort.calculateDiscountSummary(context.withCode(discountCode), baseTotal);
            discount = discountPort.discountSnapshotFromSummary(summ, baseTotal);
            finalAmount = baseTotal.subtract(discount.discountAmount());
        } catch (org.springframework.dao.DataAccessException e) {
            logger.error("Discount calculation failed due to a storage error for order {}", orderId, e);
            throw new PriceCalcException("the order total could not be calculated right now. Please try again.");
        } catch (Exception e) {
            logger.error("Error during discount application: {}", e.getMessage());
            throw new PriceCalcException(e.getMessage());
        }

        // 4. Charging the payment through the PaymentGateway
        PaymentResult paymentResult;
        try {
            paymentResult = paymentGateway.charge(orderId, finalAmount, order.getBuyerRef(), paymentToken);
        } catch (Exception e) {
            logger.error("Payment processing error: {}", e.getMessage());
            notificationPort.sendPurchaseFailure(order.getBuyerRef(), "Payment processing error: " + e.getMessage());
            throw new PaymentFailedException("Payment processing failed for order " + orderId + ": " + e.getMessage());
        }

        if (!paymentResult.success()) {
            String reason = (paymentResult.errorMessage() == null || paymentResult.errorMessage().isBlank())
                    ? "Your payment could not be completed."
                    : paymentResult.errorMessage();
            logger.warn("Checkout failed for order: Payment declined. Reason: {}", reason);
            notificationPort.sendPurchaseFailure(order.getBuyerRef(), reason);
            throw new PaymentFailedException(reason);
        }

        logger.info("Payment successful. Proceeding with ticket issuance.");

        // 5. Issuing tickets through the TicketIssuance service (distributed
        // transaction)
        IssuanceResult issuanceResult;
        try {
            List<TicketIssuanceItem> issuanceItems = buildTicketIssuanceItems(order.getItems());

            issuanceResult = ticketIssuance.issueTickets(order.getEventId(), orderId, issuanceItems, order.getBuyerRef());
            
        } catch (Exception e) {
            logger.error("Ticket issuance threw an exception for order {}. Triggering compensation.", orderId, e);

            compensateAfterTicketIssuanceFailure(
                    order,
                    paymentResult,
                    finalAmount,
                    "Ticket issuance service failed: " + e.getMessage());

            throw new IssuanceFailedException("Ticket issuance failed for order " + orderId + ": " + e.getMessage());
        }

        if (!issuanceResult.success()) {
            logger.warn(
                    "Checkout failed for order {}: ticket issuance rejected request. Reason: {}",
                    orderId,
                    issuanceResult.errorMessage());

            compensateAfterTicketIssuanceFailure(
                    order,
                    paymentResult,
                    finalAmount,
                    "Ticket issuance failed: " + issuanceResult.errorMessage());

            throw new IssuanceFailedException(
                    "Ticket issuance failed for order " + orderId + ": " + issuanceResult.errorMessage());
        }

        // 6. If all steps succeed, we create a purchase record, mark the order as
        // checked out, and send a success notification
        markReservedInventoryAsSold(order.getItems());
        EventSnapshot eventSnapshot = eventQueryPort.getEventSnapshot(order.getEventId());
        BuyerSnapshot buyerSnapshot = new BuyerSnapshot("Member " + order.getBuyerRef().memberId());

        List<PurchasedItem> purchasedItems = order.getItems().stream()
                .map(item -> new PurchasedItem(item.getZoneId(), item.getSeatId(), item.getQuantity(),
                        item.getUnitPrice()))
                .collect(Collectors.toList());

        PurchaseRecord receipt = PurchaseRecord.create(
                order.getBuyerRef().memberId(),
                buyerSnapshot,
                eventSnapshot,
                purchasedItems,
                finalAmount,
                List.of(discount),
                paymentResult.transactionId(),
                issuanceResult.issuanceConfirmationId());

        // 7. Append the purchase record, mark order as CHECKED_OUT, and send
        // notification
        purchaseRecordRepository.append(receipt);
        order.checkout();
        orderRepository.save(order);
        advanceQueueIfPossible(order.getEventId(), order.getBuyerRef());

        notificationPort.sendPurchaseSuccess(order.getBuyerRef(), receipt.getRecordId());
        logger.info(
                "Checkout process completed successfully for orderId: {}. Receipt recordId: {}",
                orderId,
                receipt.getRecordId());

        return new CheckoutResult(orderId, receipt.getRecordId(), order.getStatus().name(), finalAmount,
                paymentResult.transactionId(), issuanceResult.issuedTicketCodes());
    }

    private void compensateAfterTicketIssuanceFailure(ActiveOrder order, PaymentResult paymentResult,
            Money finalAmount, String reason) {
        requestRefund(order, paymentResult, finalAmount, reason);

        List<OrderItem> cancelledItems = order.cancel();
        releaseReservedInventory(cancelledItems);
        orderRepository.save(order);
        advanceQueueIfPossible(order.getEventId(), order.getBuyerRef());

        safeNotifyPurchaseFailure(
                order,
                "Technical error during ticket issuance. Refund was requested.");
    }

    private void requestRefund(ActiveOrder order, PaymentResult paymentResult,
            Money finalAmount, String reason) {
        try {
            paymentGateway.refund(paymentResult.transactionId(), finalAmount, reason);
            logger.info(
                    "Refund requested for order {} and transaction {}",
                    order.getOrderId(),
                    paymentResult.transactionId());
        } catch (Exception refundError) {
            logger.error(
                    "CRITICAL: refund failed for order {} and transaction {} after ticket issuance failure",
                    order.getOrderId(),
                    paymentResult.transactionId(),
                    refundError);
        }
    }

    private void releaseReservedInventory(List<OrderItem> items) {
        for (OrderItem item : items) {
            ZoneId zoneId = new ZoneId(item.getZoneId());

            zoneRepository.withLock(zoneId, () -> {
                Zone zone = zoneRepository.findById(zoneId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Zone " + item.getZoneId() + " not found while releasing reserved inventory"));

                if (zone.zoneType() == ZoneType.STANDING) {
                    zone.releaseStanding(item.getQuantity());
                } else {
                    zone.releaseSeat(requireSeatId(item, "releasing seated inventory"));
                }

                zoneRepository.save(zone);
            });
        }
    }

    private void markReservedInventoryAsSold(List<OrderItem> items) {
        for (OrderItem item : items) {
            ZoneId zoneId = new ZoneId(item.getZoneId());

            zoneRepository.withLock(zoneId, () -> {
                Zone zone = zoneRepository.findById(zoneId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Zone " + item.getZoneId() + " not found while finalizing sold inventory"));

                if (zone.zoneType() == ZoneType.STANDING) {
                    zone.markSoldStanding(item.getQuantity());
                } else {
                    zone.markSold(requireSeatId(item, "finalizing seated inventory"));
                }

                zoneRepository.save(zone);
            });
        }
    }

    private SeatId requireSeatId(OrderItem item, String operation) {
        if (item.getSeatId() == null || item.getSeatId().isBlank()) {
            throw new IllegalStateException(
                    "Missing seat id while " + operation + " in zone " + item.getZoneId());
        }

        return new SeatId(item.getSeatId());
    }

    private void safeNotifyPurchaseFailure(ActiveOrder order, String message) {
        try {
            notificationPort.sendPurchaseFailure(order.getBuyerRef(), message);
        } catch (Exception notificationError) {
            logger.warn(
                    "Failed to send purchase-failure notification for order {}",
                    order.getOrderId(),
                    notificationError);
        }
    }

    private void advanceQueueIfPossible(String eventId, BuyerReference buyer) {
        if (queueService == null) {
            return;
        }
        try {
            // The purchase is done, so take back this buyer's admission slot and let
            // the next waiting buyer in.
            queueService.releaseAdmissionAndAdmitNext(eventId, buyer);
        } catch (RuntimeException e) {
            logger.warn("Failed to advance queue immediately for event {}", eventId, e);
        }
    }

    private List<TicketIssuanceItem> buildTicketIssuanceItems(List<OrderItem> orderItems) {
        return orderItems.stream()
                .map(this::toTicketIssuanceItem)
                .toList();
    }

    private TicketIssuanceItem toTicketIssuanceItem(OrderItem item) {
        ZoneId zoneId = new ZoneId(item.getZoneId());

        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new IllegalStateException(
                        "Zone " + item.getZoneId() + " not found while preparing ticket issuance"));

        if (zone.zoneType() == ZoneType.STANDING) {
            return new TicketIssuanceItem(
                    item.getZoneId(),
                    zone.zoneName(),
                    item.getQuantity(),
                    null,
                    null,
                    null);
        }

        SeatId seatId = requireSeatId(item, "preparing seated ticket issuance");

        var seat = zone.rows().stream()
                .flatMap(row -> row.seats().stream())
                .filter(s -> s.seatId().equals(seatId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Seat " + item.getSeatId() + " not found in zone " + item.getZoneId()));

        return new TicketIssuanceItem(
                item.getZoneId(),
                zone.zoneName(),
                item.getQuantity(),
                item.getSeatId(),
                seat.rowLabel(),
                seat.seatNumber());
    }
}