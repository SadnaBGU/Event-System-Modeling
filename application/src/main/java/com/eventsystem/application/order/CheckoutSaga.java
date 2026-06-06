package com.eventsystem.application.order;

import com.eventsystem.application.appexceptions.ActiveOrderHasExpiredException;
import com.eventsystem.application.appexceptions.IssuanceFailedException;
import com.eventsystem.application.appexceptions.OrderNotFoundException;
import com.eventsystem.application.appexceptions.OrderViolatesPolicyException;
import com.eventsystem.application.appexceptions.PaymentFailedException;
import com.eventsystem.application.appexceptions.PriceCalcException;
import com.eventsystem.application.event.IEventQueryPort;
import com.eventsystem.application.event.IZoneRepository;
import com.eventsystem.application.event.IZoneServicePort;
import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.application.policy.IDiscountApplicationPort;
import com.eventsystem.application.policy.IPurchasePolicyValidationPort;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.purchaserecord.BuyerSnapshot;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.domain.purchaserecord.PurchasedItem;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.ZoneId;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public CheckoutSaga(IActiveOrderRepository orderRepository,
                        IPurchaseRecordRepository purchaseRecordRepository,
                        IPaymentGatewayPort paymentGateway,
                        ITicketIssuancePort ticketIssuance,
                        INotificationPort notificationPort,
                        IZoneRepository zoneRepository,
                        IPurchasePolicyValidationPort purchasePolicyPort,
                        IDiscountApplicationPort discountPort,
                        IEventQueryPort eventQueryPort) {
        this.orderRepository = orderRepository;
        this.purchaseRecordRepository = purchaseRecordRepository;
        this.paymentGateway = paymentGateway;
        this.ticketIssuance = ticketIssuance;
        this.notificationPort = notificationPort;
        this.zoneRepository = zoneRepository;
        this.purchasePolicyPort = purchasePolicyPort;
        this.discountPort = discountPort;
        this.eventQueryPort = eventQueryPort;
    }

    /**
     * This method orchestrates the entire checkout process for an active order. It includes:
     * 1. Validating the order and its expiration.
     * 2. Validating purchase policies before discounts (as required by Stream 4 instructions).
     * 3. Calculating the total price and applying discounts.
     * 4. Charging the payment through the PaymentGateway.
     * 5. Issuing tickets through the TicketIssuance service (distributed transaction).
     * 6. If any step fails, compensating actions are taken (e.g., refunding payment, unlocking seats).
     * 7. If all steps succeed, a purchase record is created, the order is marked as checked out, and a success notification is sent.
      * 
      * Note: This method assumes that the caller has already verified that the order exists and belongs to the buyer.
     */
    public void executeCheckout(String orderId, String paymentToken, String discountCode) {
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
        PurchaseContext context = purchasePolicyPort.createPurchaseContext( new EventId(order.getEventId()), order.getBuyerRef(), order.getItems());
        PolicyValidationResult policyValidationResult;
        try {
            policyValidationResult = purchasePolicyPort.evaluatePurchasePolicyFor(context);
        } catch (Exception e) {
            logger.error("Error during purchase policy validation: {}", e.getMessage());
            throw new OrderViolatesPolicyException("Error validating purchase policy for order " + orderId + ": " + e.getMessage());
        }
        
        if (!policyValidationResult.isSuccess()) {
            logger.warn("Checkout failed: Order violates purchase policy for event {}", order.getEventId());
            logger.warn("Reason: {}", policyValidationResult.reason());
            throw new OrderViolatesPolicyException("Order" + orderId + " violates purchase policy for event " + order.getEventId() +"Reason:" + policyValidationResult.reason());
        }

        // 3. Calculating the total price and applying discounts
        Money finalAmount;
        DiscountSnapshot discount;
        try {
            Money baseTotal = order.calculateBaseTotal();
            discount = discountPort.generateDiscountSnapshot(context.withCode(discountCode), baseTotal);
            finalAmount = baseTotal.subtract(discount.discountAmount());
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
            logger.warn("Checkout failed for order: Payment declined. Reason: {}", paymentResult.errorMessage());
            notificationPort.sendPurchaseFailure(order.getBuyerRef(), "Payment declined");
            throw new PaymentFailedException(paymentResult.errorMessage());
        }

        logger.info("Payment successful. Proceeding with ticket issuance.");

        // 5. Issuing tickets through the TicketIssuance service (distributed transaction)
        IssuanceResult issuanceResult;
        try {
            issuanceResult = ticketIssuance.issueTickets(order.getEventId(), orderId, order.getItems(), order.getBuyerRef());
        } catch (Exception e) {
            // If an exception occurs during ticket issuance, we need to compensate by refunding the payment and unlocking any reserved seats
            logger.error("System crash during ticket issuance. Triggering compensating actions (Refund & Unlock).", e);
            paymentGateway.refund(paymentResult.transactionId(), finalAmount, "Ticket issuance service crashed: " + e.getMessage());
            
            releaseReservedSeats(order.getItems());
            
            notificationPort.sendPurchaseFailure(order.getBuyerRef(), "System error during ticket issuance. You have been refunded.");
            throw new IssuanceFailedException("Ticket issuance failed for order " + orderId + ": " + e.getMessage());
        }
        
        if (!issuanceResult.success()) {
            logger.warn("Checkout failed for order: Ticket issuance logic rejected the request. Reason: {}", issuanceResult.errorMessage());
            // If ticket issuance fails (e.g., due to business logic), we also need to compensate by refunding the payment and unlocking any reserved seats
            paymentGateway.refund(paymentResult.transactionId(), finalAmount, "Ticket issuance failed: " + issuanceResult.errorMessage());
            
            releaseReservedSeats(order.getItems());
            
            notificationPort.sendPurchaseFailure(order.getBuyerRef(), "Technical error during ticket issuance. You have been refunded.");
            throw new IssuanceFailedException("Ticket issuance failed for order " + orderId + ": " + issuanceResult.errorMessage());
        }

        // 6. If all steps succeed, we create a purchase record, mark the order as checked out, and send a success notification
        EventSnapshot eventSnapshot = eventQueryPort.getEventSnapshot(order.getEventId());
        BuyerSnapshot buyerSnapshot = new BuyerSnapshot("Member " + order.getBuyerRef().memberId());
        
        List<PurchasedItem> purchasedItems = order.getItems().stream()
                .map(item -> new PurchasedItem(item.getZoneId(), item.getSeatId(), item.getQuantity(), item.getUnitPrice()))
                .collect(Collectors.toList());

        PurchaseRecord receipt = PurchaseRecord.create(
                order.getBuyerRef().memberId(),
                buyerSnapshot,
                eventSnapshot,
                purchasedItems,
                finalAmount,
                List.of(discount),
                paymentResult.transactionId(),
                issuanceResult.issuanceConfirmationId()
        );

        // 7. Append the purchase record, mark order as CHECKED_OUT, and send notification
        purchaseRecordRepository.append(receipt);
        order.checkout();
        orderRepository.save(order); 
        
        notificationPort.sendPurchaseSuccess(order.getBuyerRef(), receipt.recordId());
        logger.info("Checkout process completed successfully for orderId: {}. Receipt recordId: {}", orderId, receipt.recordId());
    }

    private void releaseReservedSeats(List<OrderItem> items) {
        for (OrderItem item : items) {
            ZoneId zId = new ZoneId(item.getZoneId());
            zoneRepository.withLock(zId, () -> {
                zoneRepository.findById(zId).ifPresent(zone -> {
                    zone.releaseSeat(new SeatId(item.getSeatId()));
                    zoneRepository.save(zone);
                });
            });
        }
    }
}