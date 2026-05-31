package com.eventsystem.application.admin;

import com.eventsystem.application.appexceptions.NotAuthorizedException;
import com.eventsystem.application.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;
import com.eventsystem.domain.member.Suspension;
import com.eventsystem.domain.platform.Platform;
import com.eventsystem.application.admin.IPlatformRepository;
import com.eventsystem.domain.shared.ProviderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Use cases (I.1, I.2): manage the singleton {@link Platform} — admin set,
 * provider registries, global tunables, lifecycle.
 * Use cases (II.6.7, II.6.8, II.6.9): suspend, unsuspend, and view member suspensions.
 *
 * Every mutating call requires the {@code actor} to already be a system administrator.
 */
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final IPlatformRepository platformRepo;
    private final IMemberRepository memberRepo;

    public AdminService(IPlatformRepository platformRepo, IMemberRepository memberRepo) {
        this.platformRepo = platformRepo;
        this.memberRepo = memberRepo;
    }

    public PlatformDto getPlatform(MemberId actor) {
        Platform p = requireAdmin(actor);
        return toDto(p);
    }

    public void activate(MemberId actor) {
        Platform p = requireAdmin(actor);
        p.activate();
        platformRepo.save(p);
        log.info("Platform activated by admin={}", actor.value());
    }

    public void shutdown(MemberId actor) {
        Platform p = requireAdmin(actor);
        p.shutdown();
        platformRepo.save(p);
        log.info("Platform shutdown by admin={}", actor.value());
    }

    public void addAdmin(MemberId actor, MemberId newAdmin) {
        Objects.requireNonNull(newAdmin, "newAdmin must not be null");
        if (memberRepo.findById(newAdmin).isEmpty()) {
            throw new IllegalArgumentException("Cannot promote unknown member: " + newAdmin.value());
        }
        Platform p = requireAdmin(actor);
        p.addAdmin(newAdmin);
        platformRepo.save(p);
        log.info("Admin {} promoted by admin={}", newAdmin.value(), actor.value());
    }

    public void removeAdmin(MemberId actor, MemberId target) {
        Objects.requireNonNull(target, "target must not be null");
        Platform p = requireAdmin(actor);
        p.removeAdmin(target); // domain rejects removing the last admin
        platformRepo.save(p);
        log.info("Admin {} demoted by admin={}", target.value(), actor.value());
    }

    public void addPaymentProvider(MemberId actor, ProviderId provider) {
        Platform p = requireAdmin(actor);
        p.addPaymentProvider(provider);
        platformRepo.save(p);
        log.info("Payment provider {} added by admin={}", provider.value(), actor.value());
    }

    public void removePaymentProvider(MemberId actor, ProviderId provider) {
        Platform p = requireAdmin(actor);
        p.removePaymentProvider(provider);
        platformRepo.save(p);
        log.info("Payment provider {} removed by admin={}", provider.value(), actor.value());
    }

    public void addIssuanceProvider(MemberId actor, ProviderId provider) {
        Platform p = requireAdmin(actor);
        p.addIssuanceProvider(provider);
        platformRepo.save(p);
        log.info("Issuance provider {} added by admin={}", provider.value(), actor.value());
    }

    public void removeIssuanceProvider(MemberId actor, ProviderId provider) {
        Platform p = requireAdmin(actor);
        p.removeIssuanceProvider(provider);
        platformRepo.save(p);
        log.info("Issuance provider {} removed by admin={}", provider.value(), actor.value());
    }

    public void setDefaultReservationTimeout(MemberId actor, Duration timeout) {
        Platform p = requireAdmin(actor);
        p.setDefaultReservationTimeout(timeout);
        platformRepo.save(p);
        log.info("Default reservation timeout set to {} by admin={}", timeout, actor.value());
    }

    public void setQueueLoadThreshold(MemberId actor, int threshold) {
        Platform p = requireAdmin(actor);
        p.setQueueLoadThreshold(threshold);
        platformRepo.save(p);
        log.info("Queue load threshold set to {} by admin={}", threshold, actor.value());
    }

    // ── II.6.7 — Suspend member ──────────────────────────────────────────────

    /**
     * Suspends a member for the given duration.
     * @param duration how long to suspend; {@code null} means permanent
     * @param reason the reason for the suspension (optional, may be null or empty)
     */
    public void suspendMember(MemberId actor, MemberId target, Duration duration, String reason) {
        requireAdmin(actor);
        Objects.requireNonNull(target, "target must not be null");
        Member member = loadMember(target);
        member.suspend(Instant.now(), duration, reason);
        memberRepo.save(member);
        log.info("Member {} suspended by admin={}, duration={}, reason={}", target.value(), actor.value(),
                duration == null ? "PERMANENT" : duration, reason != null ? reason : "N/A");
    }

    // ── II.6.8 — Unsuspend member ────────────────────────────────────────────

    public void unsuspendMember(MemberId actor, MemberId target) {
        requireAdmin(actor);
        Objects.requireNonNull(target, "target must not be null");
        Member member = loadMember(target);
        member.unsuspend();
        memberRepo.save(member);
        log.info("Member {} unsuspended by admin={}", target.value(), actor.value());
    }

    // ── II.6.9 — View suspensions ────────────────────────────────────────────

    /**
     * Returns all members currently in SUSPENDED status, with their suspension details.
     */
    public List<SuspensionDto> listSuspensions(MemberId actor) {
        requireAdmin(actor);
        Instant now = Instant.now();
        List<SuspensionDto> suspensions = new ArrayList<>();
        for (Member member : memberRepo.findAll()) {
            if (!member.isSuspendedAt(now)) {
                continue;
            }
            Suspension suspension = member.getSuspension().orElseThrow();
            suspensions.add(new SuspensionDto(
                    member.getMemberId().value(),
                    member.getUsername(),
                    suspension.suspendedAt(),
                    suspension.isPermanent() ? "PERMANENT" : suspension.duration().toString(),
                    suspension.endsAt()
            ));
        }
        return suspensions;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Platform requireAdmin(MemberId actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        Platform p = platformRepo.findInstance()
                .orElseThrow(() -> new IllegalStateException("Platform has not been initialised"));
        if (!p.isAdmin(actor)) {
            throw new NotAuthorizedException(actor.value());
        }
        return p;
    }

    private Member loadMember(MemberId memberId) {
        return memberRepo.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId.value()));
    }

    private static PlatformDto toDto(Platform p) {
        return new PlatformDto(
                p.getStatus(),
                p.getSystemAdmins(),
                p.getPaymentProviders(),
                p.getIssuanceProviders(),
                p.getDefaultReservationTimeout(),
                p.getQueueLoadThreshold());
    }
}
