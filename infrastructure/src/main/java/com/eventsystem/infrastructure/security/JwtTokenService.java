package com.eventsystem.infrastructure.security;

import com.eventsystem.application.security.ITokenService;
import com.eventsystem.domain.member.MemberId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

/**
 * JWT-backed adapter for {@link ITokenService}.
 *
 * Uses HS256 with a symmetric secret. Secret must be at least 32 bytes (256 bits)
 * per RFC 7518 §3.2.
 */
public class JwtTokenService implements ITokenService {

    private static final String ISSUER = "event-system";

    private final SecretKey signingKey;

    public JwtTokenService(String secret) {
        Objects.requireNonNull(secret, "jwt-secret must not be null");
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException(
                    "eventsystem.security.jwt-secret must be at least 32 bytes (got " + bytes.length + ")");
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
    }

    @Override
    public String issueToken(MemberId subject, Duration validity) {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(validity, "validity must not be null");
        if (validity.isZero() || validity.isNegative()) {
            throw new IllegalArgumentException("validity must be positive");
        }

        Instant now = Instant.now();
        Instant expiry = now.plus(validity);

        return Jwts.builder()
                .issuer(ISSUER)
                .subject(subject.value())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public TokenClaims verifyToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("token must not be blank");
        }
        try {
            Jws<Claims> parsed = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token);

            Claims claims = parsed.getPayload();
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new InvalidTokenException("token has no subject");
            }
            return new TokenClaims(
                    new MemberId(subject),
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant());
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("token has expired", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("invalid token: " + e.getMessage(), e);
        }
    }
}
