package com.eventsystem.domain.queue;

import java.time.Instant;
import java.util.Objects;
import jakarta.persistence.*;
import com.eventsystem.domain.order.BuyerReference;

@Embeddable
public class QueueEntry {

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "visitor_ref_id", nullable = false))
    })
    private BuyerReference visitorRef;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "position_in_queue", nullable = false)
    private int position;

    protected QueueEntry() {}

    public QueueEntry(BuyerReference visitorRef, int position) {
        this.visitorRef = visitorRef;
        this.joinedAt = Instant.now();
        this.position = position;
    }

    public BuyerReference getVisitorRef() { return visitorRef; }
    public int getPosition() { return position; }
    public Instant getJoinedAt() { return joinedAt; }
}