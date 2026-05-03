package com.eventsystem.domain.queue;

import java.time.Instant;

import com.eventsystem.domain.order.BuyerReference;

public class QueueEntry {
    private final BuyerReference visitorRef;
    private final Instant joinedAt;
    private final int position;

    public QueueEntry(BuyerReference visitorRef, int position) {
        this.visitorRef = visitorRef;
        this.joinedAt = Instant.now();
        this.position = position;
    }

    public BuyerReference getVisitorRef() { return visitorRef; }
    public int getPosition() { return position; }
}