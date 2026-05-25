package com.eventsystem.application.lottery;

import com.eventsystem.domain.order.BuyerReference;

public interface ILotteryValidationPort {
    boolean isLotteryEvent(String eventId);
    boolean validateWinnerCode(String eventId, BuyerReference buyer, String authCode); 
}
