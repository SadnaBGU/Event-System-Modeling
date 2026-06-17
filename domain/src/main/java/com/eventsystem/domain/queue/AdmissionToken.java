package com.eventsystem.domain.queue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import jakarta.persistence.*;
import com.eventsystem.domain.order.BuyerReference;

@Embeddable
public class AdmissionToken {

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "buyer_ref_id", nullable = false))
    })
    private BuyerReference buyerRef;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    protected AdmissionToken() {}

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
