package com.eventsystem.application.order;

import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderItem;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentGatewayPort {
    
    PaymentResult charge(String orderId, BigDecimal amount, BuyerReference buyer, String paymentDetailsToken);
    
    RefundResult refund(String transactionId, BigDecimal amount, String reason);
}