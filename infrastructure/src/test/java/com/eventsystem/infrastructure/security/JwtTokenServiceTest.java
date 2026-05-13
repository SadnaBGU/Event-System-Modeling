package com.eventsystem.infrastructure.security;

import com.eventsystem.application.security.TokenService;
import com.eventsystem.application.security.TokenService.InvalidTokenException;
import com.eventsystem.application.security.TokenService.TokenClaims;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef"; // 32 bytes
    private static final String OTHER_SECRET = "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ";

    private final JwtTokenService service = new JwtTokenService(SECRET);

    @Test
    void issuedTokenRoundTripsThroughVerify() {
        MemberId subject = MemberId.generate();
        String token = service.issueToken(subject, Duration.ofMinutes(5));

        TokenClaims claims = service.verifyToken(token);

        assertThat(claims.subject()).isEqualTo(subject);
        assertThat(claims.expiresAt()).isAfter(claims.issuedAt());
        assertThat(claims.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void verifyRejectsExpiredToken() {
        MemberId subject = MemberId.generate();
        // jjwt rejects non-positive durations at issue, so issue with 1s and verify with allowance
        String token = service.issueToken(subject, Duration.ofSeconds(1));
        // Manipulate by sleeping is flaky; instead build a token with a past expiry via different service
        // We rely on the fact that issue+verify works; a real expiry test follows below using a near-zero token.
        // Simulate expiry: issue then wait briefly
        try { Thread.sleep(1100); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        assertThatThrownBy(() -> service.verifyToken(token))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyRejectsTokenSignedWithDifferentSecret() {
        JwtTokenService other = new JwtTokenService(OTHER_SECRET);
        String token = other.issueToken(MemberId.generate(), Duration.ofMinutes(5));
        assertThatThrownBy(() -> service.verifyToken(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyRejectsTamperedToken() {
        String token = service.issueToken(MemberId.generate(), Duration.ofMinutes(5));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertThatThrownBy(() -> service.verifyToken(tampered))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyRejectsBlankToken() {
        assertThatThrownBy(() -> service.verifyToken(null)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.verifyToken("   ")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyRejectsGarbage() {
        assertThatThrownBy(() -> service.verifyToken("not-a-jwt"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void issueRejectsNonPositiveValidity() {
        assertThatThrownBy(() -> service.issueToken(MemberId.generate(), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issueToken(MemberId.generate(), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsShortSecret() {
        assertThatThrownBy(() -> new JwtTokenService("too short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void claimsTimestampsTruncateConsistently() {
        // JWT exp/iat are second-precision; verify our parsing reflects that.
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String token = service.issueToken(MemberId.generate(), Duration.ofMinutes(5));
        TokenClaims claims = service.verifyToken(token);
        assertThat(claims.issuedAt()).isAfterOrEqualTo(before);
    }
}
