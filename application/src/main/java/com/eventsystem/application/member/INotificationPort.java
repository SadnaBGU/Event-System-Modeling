package com.eventsystem.application.member;

import com.eventsystem.domain.order.BuyerReference;

public interface INotificationPort {
    
    void sendPurchaseSuccess(BuyerReference buyer, String receiptId);
    
    void sendPurchaseFailure(BuyerReference buyer, String reason);

    void sendQueueTurnArrived(BuyerReference buyer, String eventId);
    
    void sendEventSoldOut(BuyerReference buyer, String eventId);

    /** Notify a lottery winner that they won and give them their time-limited purchase code. */
    void sendLotteryWon(BuyerReference buyer, String eventId, String permissionCode);
}
