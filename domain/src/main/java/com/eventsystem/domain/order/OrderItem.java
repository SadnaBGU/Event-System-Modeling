package com.eventsystem.domain.order;

import java.math.BigDecimal;

import com.eventsystem.domain.shared.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

@Embeddable
public class OrderItem {

    private String zoneId;
    private String seatId;
    private int quantity;

    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "price_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "price_currency"))
    })
    private Money unitPrice;

    public OrderItem() {}

    public OrderItem(String zoneId, String seatId, int quantity, Money unitPrice) {
        this.zoneId = zoneId;
        this.seatId = seatId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }
    
    public String getZoneId() { 
        return zoneId; 
    }

    public String getSeatId() { 
        return seatId; 
    }

    public int getQuantity() { 
        return quantity; 
    }

    public Money getUnitPrice() { 
        return unitPrice;
    }
}