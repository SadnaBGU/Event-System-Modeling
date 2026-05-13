package com.eventsystem.domain.queue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.eventsystem.domain.order.BuyerReference;

public class AdmissionToken {
    private final BuyerReference buyerRef;
    private final Instant expiresAt;
    private boolean consumed;

    public AdmissionToken(BuyerReference buyerRef, int validityMinutes) {
        this.buyerRef = buyerRef;
        this.expiresAt = Instant.now().plus(validityMinutes, ChronoUnit.MINUTES);
        this.consumed = false;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void markConsumed() { 
        this.consumed = true; 
    }

    public boolean isConsumed() { 
        return consumed; 
    }

    public BuyerReference getBuyerRef() { 
        return buyerRef; 
    }
}
