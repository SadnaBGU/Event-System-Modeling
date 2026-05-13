package com.eventsystem.domain.order;

import java.math.BigDecimal;

import com.eventsystem.domain.shared.Money;

public class OrderItem {
    private String zoneId;
    private String seatId;
    private int quantity;
    private Money unitPrice;

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