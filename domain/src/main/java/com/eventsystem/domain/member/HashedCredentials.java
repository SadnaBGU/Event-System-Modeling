package com.eventsystem.domain.member;

import java.util.Objects;

import jakarta.persistence.Embeddable;

/**
 * Value Object — opaque hashed-password material for a member.
 * The domain layer never sees the plaintext; hashing is performed by the infrastructure layer.
 */
@Embeddable
public record HashedCredentials(String hash, String salt, String algorithm) {

    public HashedCredentials {
        Objects.requireNonNull(hash, "hash must not be null");
        Objects.requireNonNull(salt, "salt must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        if (hash.isBlank()) {
            throw new IllegalArgumentException("hash must not be blank");
        }
        if (algorithm.isBlank()) {
            throw new IllegalArgumentException("algorithm must not be blank");
        }
    }
}
