package com.eventsystem.application.admin;

import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberRepository;
import com.eventsystem.domain.platform.Platform;
import com.eventsystem.domain.platform.PlatformRepository;
import com.eventsystem.domain.shared.ProviderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

/**
 * Use cases (I.1, I.2): manage the singleton {@link Platform} — admin set,
 * provider registries, global tunables, lifecycle.
 *
 * Every mutating call requires the {@code actor} to already be a system administrator.
 */
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final PlatformRepository platformRepo;
    private final MemberRepository memberRepo;

    public AdminService(PlatformRepository platformRepo, MemberRepository memberRepo) {
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

    private Platform requireAdmin(MemberId actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        Platform p = platformRepo.findInstance()
                .orElseThrow(() -> new IllegalStateException("Platform has not been initialised"));
        if (!p.isAdmin(actor)) {
            throw new NotAuthorizedException(actor.value());
        }
        return p;
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
