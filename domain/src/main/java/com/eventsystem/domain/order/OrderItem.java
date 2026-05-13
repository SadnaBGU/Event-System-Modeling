package com.eventsystem.domain.order;

import java.math.BigDecimal;

public class OrderItem {
    private String zoneId;
    private String seatId;
    private int quantity;
    private BigDecimal unitPrice;

    public OrderItem(String zoneId, String seatId, int quantity, BigDecimal unitPrice) {
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

    public BigDecimal getUnitPrice() { 
        return unitPrice;
    }
}