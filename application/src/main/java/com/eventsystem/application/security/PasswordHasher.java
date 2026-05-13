package com.eventsystem.application.security;

import com.eventsystem.domain.member.HashedCredentials;

/**
 * Port — hashes plaintext passwords and verifies them against {@link HashedCredentials}.
 * The application layer depends on this abstraction; infrastructure provides the BCrypt impl.
 */
public interface PasswordHasher {

    /** Hash a plaintext password into storable {@link HashedCredentials}. */
    HashedCredentials hash(String plaintext);

    /** Constant-time verify of a plaintext password against stored credentials. */
    boolean matches(String plaintext, HashedCredentials credentials);
}
