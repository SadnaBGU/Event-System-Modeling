package com.eventsystem.application.member;

import com.eventsystem.domain.order.BuyerReference;
import java.math.BigDecimal;
import java.util.List;

public interface INotificationPort {
    
    void sendPurchaseSuccess(BuyerReference buyer, String receiptId);
    
    void sendPurchaseFailure(BuyerReference buyer, String reason);

    void sendQueueTurnArrived(BuyerReference buyer, String eventId);
    
    void sendEventSoldOut(BuyerReference buyer, String eventId);
}
