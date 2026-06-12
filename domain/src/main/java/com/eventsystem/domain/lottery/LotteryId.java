package com.eventsystem.domain.lottery;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class LotteryId implements Serializable {

    @Column(name = "id")
    private String value;

    protected LotteryId() {}

    public LotteryId(String value) {
        Objects.requireNonNull(value, "LotteryId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("LotteryId value must not be blank");
        }
        this.value = value;
    }

    public static LotteryId generate() {
        return new LotteryId(UUID.randomUUID().toString());
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LotteryId lotteryId = (LotteryId) o;
        return Objects.equals(value, lotteryId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}