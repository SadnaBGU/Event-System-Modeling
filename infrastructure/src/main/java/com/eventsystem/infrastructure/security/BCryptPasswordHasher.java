package com.eventsystem.infrastructure.security;

import com.eventsystem.application.security.IPasswordHasher;
import com.eventsystem.domain.member.HashedCredentials;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Objects;

/**
 * BCrypt-backed adapter for {@link IPasswordHasher}.
 *
 * Note: {@link BCryptPasswordEncoder} produces a self-contained hash string that already
 * embeds the algorithm version, cost, and salt. We therefore store the full encoded value
 * in {@link HashedCredentials#hash()}, leave {@link HashedCredentials#salt()} as a marker
 * (kept for VO shape compatibility), and tag the algorithm as {@code "BCrypt"}.
 */
public class BCryptPasswordHasher implements IPasswordHasher {

    public static final String ALGORITHM = "BCrypt";
    private static final String EMBEDDED_SALT_MARKER = "embedded";

    private final BCryptPasswordEncoder encoder;

    public BCryptPasswordHasher(int strength) {
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    @Override
    public HashedCredentials hash(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        if (plaintext.isEmpty()) {
            throw new IllegalArgumentException("plaintext must not be empty");
        }
        String encoded = encoder.encode(plaintext);
        return new HashedCredentials(encoded, EMBEDDED_SALT_MARKER, ALGORITHM);
    }

    @Override
    public boolean matches(String plaintext, HashedCredentials credentials) {
        Objects.requireNonNull(credentials, "credentials must not be null");
        if (plaintext == null || plaintext.isEmpty()) {
            return false;
        }
        if (!ALGORITHM.equals(credentials.algorithm())) {
            throw new IllegalArgumentException(
                    "Unsupported credentials algorithm: " + credentials.algorithm());
        }
        return encoder.matches(plaintext, credentials.hash());
    }
}
