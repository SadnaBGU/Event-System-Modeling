package com.eventsystem.application.auth;

import com.eventsystem.application.security.PasswordHasher;
import com.eventsystem.application.security.TokenService;
import com.eventsystem.application.security.TokenService.TokenClaims;
import com.eventsystem.domain.member.HashedCredentials;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberRepository;
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

    private final MemberRepository members;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;
    private final Duration tokenValidity;

    public AuthService(MemberRepository members,
                       PasswordHasher passwordHasher,
                       TokenService tokenService,
                       Duration tokenValidity) {
        this.members = members;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.tokenValidity = tokenValidity;
    }

    /** Register a new member. Throws {@link UsernameAlreadyTakenException} on collision. */
    public MemberId register(RegisterMemberRequest req) {
        Objects.requireNonNull(req, "request must not be null");
        validateRegisterRequest(req);

        if (members.findByUsername(req.username()).isPresent()) {
            throw new UsernameAlreadyTakenException(req.username());
        }

        HashedCredentials creds = passwordHasher.hash(req.plaintextPassword());
        PersonalDetails details = new PersonalDetails(
                req.firstName(), req.lastName(), req.email(), req.dateOfBirth());
        Member member = new Member(MemberId.generate(), req.username(), creds, details);

        // save() rejects duplicate username atomically — wins the race if two threads register the same name
        members.save(member);
        log.info("Registered new member username={} memberId={}", member.getUsername(), member.getMemberId().value());
        return member.getMemberId();
    }

    /** Authenticate by username + password and return a fresh bearer token. */
    public LoginResponse login(LoginRequest req) {
        Objects.requireNonNull(req, "request must not be null");
        if (req.username() == null || req.username().isBlank()
                || req.plaintextPassword() == null || req.plaintextPassword().isEmpty()) {
            throw new AuthenticationException("Invalid credentials");
        }

        Member member = members.findByUsername(req.username())
                .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

        if (member.getStatus() == MemberStatus.CANCELLED) {
            throw new AuthenticationException("Invalid credentials");
        }
        if (!passwordHasher.matches(req.plaintextPassword(), member.getHashedCredentials())) {
            log.info("Failed login attempt username={}", req.username());
            throw new AuthenticationException("Invalid credentials");
        }
        String token = tokenService.issueToken(member.getMemberId(), tokenValidity);
        TokenClaims claims = tokenService.verifyToken(token);
        log.info("Member logged in memberId={}", member.getMemberId().value());
        return new LoginResponse(token, member.getMemberId(), claims.expiresAt());
    }

    /** Resolve a bearer token to its subject {@link MemberId}, validating signature + expiry. */
    public MemberId authenticate(String bearerToken) {
        TokenClaims claims = tokenService.verifyToken(bearerToken);
        return claims.subject();
    }

    private void validateRegisterRequest(RegisterMemberRequest req) {
        requireNonBlank(req.username(), "username");
        Objects.requireNonNull(req.plaintextPassword(), "plaintextPassword must not be null");
        if (req.plaintextPassword().length() < 8) {
            throw new IllegalArgumentException("password must be at least 8 characters");
        }
        requireNonBlank(req.firstName(), "firstName");
        requireNonBlank(req.lastName(), "lastName");
        requireNonBlank(req.email(), "email");
        Objects.requireNonNull(req.dateOfBirth(), "dateOfBirth must not be null");
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
