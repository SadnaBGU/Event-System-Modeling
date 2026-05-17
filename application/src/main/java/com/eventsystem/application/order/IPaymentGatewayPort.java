package com.eventsystem.application.order;

import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.shared.Money;

import java.math.BigDecimal;
import java.util.List;

public interface IPaymentGatewayPort {
    
    PaymentResult charge(String orderId, Money amount, BuyerReference buyer, String paymentDetailsToken);
    
    RefundResult refund(String transactionId, Money amount, String reason);
}