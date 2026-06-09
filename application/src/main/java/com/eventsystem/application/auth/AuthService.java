package com.eventsystem.application.auth;

import com.eventsystem.application.appexceptions.AuthenticationException;
import com.eventsystem.application.appexceptions.UsernameAlreadyTakenException;
import com.eventsystem.application.security.IPasswordHasher;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.application.security.ITokenService.TokenClaims;
import com.eventsystem.domain.member.HashedCredentials;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;
import com.eventsystem.domain.member.PersonalDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

/**
 * Use cases: register a new member (II.1), login (II.1), token resolution.
 *
 * Logout is intentionally absent: tokens are stateless and short-lived;
 * "logging out" is the client discarding the token. A token-revocation list
 * can be added later without changing this interface.
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final ITokenService tokenService;

    public AuthService(ITokenService tokenService) {
        this.tokenService = tokenService;
    }

    /** Resolve a bearer token to its subject {@link MemberId}, validating signature + expiry. */
    public MemberId authenticate(String bearerToken) {
        TokenClaims claims = tokenService.verifyToken(bearerToken);
        return claims.subject();
    }
}
