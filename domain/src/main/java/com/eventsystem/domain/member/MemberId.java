package com.eventsystem.domain.member;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
<<<<<<< HEAD
import jakarta.persistence.Embeddable;
/**
 * Value Object — opaque identifier for a Member aggregate root.
 */
@Embeddable
public record MemberId(String value) implements Serializable{
=======

import jakarta.persistence.Embeddable;

/**
 * Value Object — opaque identifier for a Member aggregate root.
 */

@Embeddable
public record MemberId(String value) {
>>>>>>> 7bd8db0f9368a60de4d5e81f22dd931c1576c4f1

    public MemberId {
        Objects.requireNonNull(value, "MemberId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("MemberId value must not be blank");
        }
    }

    public static MemberId generate() {
        return new MemberId(UUID.randomUUID().toString());
    }

    /** Alias for {@link #generate()} — keeps compatibility with existing test code. */
    public static MemberId random() {
        return generate();
    }
}
