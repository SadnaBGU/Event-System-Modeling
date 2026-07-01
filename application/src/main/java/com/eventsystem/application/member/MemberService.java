package com.eventsystem.application.member;

import com.eventsystem.application.appexceptions.AuthenticationException;
import com.eventsystem.application.appexceptions.MemberNotFoundException;
import com.eventsystem.application.appexceptions.UsernameAlreadyTakenException;
import com.eventsystem.application.auth.LoginRequest;
import com.eventsystem.application.auth.LoginResponse;
import com.eventsystem.application.auth.RegisterMemberRequest;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Use cases: get/update member details (II.3.4), cancel account (II.6.2).
 *
 * All write operations require an authenticated {@code actor} {@link MemberId}.
 * In V1 a member may only act on themselves; cross-member operations are an admin concern.
 */
public class MemberService implements IMemberInformationPort{

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final IMemberRepository members;
    private final IPasswordHasher passwordHasher;
    private final ITokenService tokenService;
    private final Duration tokenValidity;


    public MemberService(IMemberRepository members, 
                          IPasswordHasher passwordHasher,
                          ITokenService tokenService,
                          Duration tokenValidity
    ) {
        this.members = members;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.tokenValidity = tokenValidity;
    }

    public MemberDto getDetails(MemberId actor, MemberId target) {
        requireSelf(actor, target);
        Member m = load(target);
        return MemberDto.from(m);
    }

    public MemberDto updateDetails(MemberId actor, MemberId target, UpdateMemberDetailsRequest req) {
        Objects.requireNonNull(req, "request must not be null");
        requireSelf(actor, target);

        Member m = load(target);
        PersonalDetails newDetails = new PersonalDetails(req.dateOfBirth(), req.email(), 
                req.firstName(), req.lastName());
        m.updateDetails(newDetails); // domain enforces non-null + cancelled-guard
        members.save(m);
        log.info("Member updated own details memberId={}", target.value());
        return MemberDto.from(m);
    }

    public void cancelOwnAccount(MemberId actor, MemberId target) {
        requireSelf(actor, target);
        Member m = load(target);
        m.cancel();
        members.save(m);
        log.info("Member cancelled own account memberId={}", target.value());
    }

    private Member load(MemberId id) {
        return members.findById(id).orElseThrow(() -> new MemberNotFoundException(id));
    }

    private static void requireSelf(MemberId actor, MemberId target) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (!actor.equals(target)) {
            throw new SecurityException("Actor " + actor.value()
                    + " is not authorized to act on member " + target.value());
        }
    }


    //Added for policy checking
    @Override
    public LocalDate getMemberBirthdate(MemberId memberId) {
        return load(memberId).getPersonalDetails().dateOfBirth();
    }

    @Override
    public MemberStatus getMemberStatus(MemberId memberId) {
        return load(memberId).getStatus();
    }

    /** Register a new member. Throws {@link UsernameAlreadyTakenException} on collision. */
    public MemberId register(RegisterMemberRequest req) {
        Objects.requireNonNull(req, "request must not be null");
        validateRegisterRequest(req);

        if (members.findByUsername(req.username()).isPresent()) {
            throw new UsernameAlreadyTakenException(req.username());
        }

        HashedCredentials creds = passwordHasher.hash(req.plaintextPassword());
        PersonalDetails details = new PersonalDetails(req.dateOfBirth(), req.email(), 
                req.firstName(), req.lastName());
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

        // Keep status in sync so expired temporary suspensions can log in again.
        member.refreshSuspensionStatusAt(Instant.now());

        if (member.getStatus() == MemberStatus.CANCELLED) {
            throw new AuthenticationException("User account has been cancelled");
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

    @Transactional
    public List<NotificationDto> getAndMarkPendingNotifications(MemberId actor, boolean markAsRead) {
        Member m;
        if (markAsRead) {
            m = members.findByIdForUpdate(actor)
                    .orElseThrow(() -> new MemberNotFoundException(actor));
        } else {
            m = members.findById(actor)
                    .orElseThrow(() -> new MemberNotFoundException(actor));
        }

        List<NotificationDto> pending = m.getUndeliveredNotifications().stream()
                .map(n -> new NotificationDto(n.getNotificationId(), n.getType().name(), n.getContent(), n.getCreatedAt().toString(), n.isDelivered()))
                .collect(Collectors.toList());

        if (markAsRead && !pending.isEmpty()) {
            m.markNotificationsDelivered();
            members.save(m);
        }

        return pending;
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
