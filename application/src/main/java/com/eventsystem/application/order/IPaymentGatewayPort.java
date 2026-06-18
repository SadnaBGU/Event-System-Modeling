package com.eventsystem.application.order;

import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.shared.Money;

public interface IPaymentGatewayPort {
    
    PaymentResult charge(String orderId, Money amount, BuyerReference buyer, String paymentDetailsToken);
    
    RefundResult refund(String transactionId, Money amount, String reason);
}