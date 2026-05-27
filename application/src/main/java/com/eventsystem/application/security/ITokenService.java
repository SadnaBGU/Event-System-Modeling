package com.eventsystem.application.security;

import com.eventsystem.domain.member.MemberId;

import java.time.Duration;
import java.time.Instant;

/**
 * Port — issues and validates stateless authentication tokens (JWT).
 */
public interface ITokenService {

    /** Issue a token for the given subject member, valid for the given duration. */
    String issueToken(MemberId subject, Duration validity);

    /** Parse, verify signature, and check expiry. Throws {@link InvalidTokenException} if invalid. */
    TokenClaims verifyToken(String token);

    record TokenClaims(MemberId subject, Instant issuedAt, Instant expiresAt) { }

    class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }

        public InvalidTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
