package com.eventsystem.domain.platform;

import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.shared.ProviderId;
import jakarta.persistence.*;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregate Root — singleton platform configuration.
 * Holds the set of system administrators (referenced by {@link MemberId}),
 * registered payment / issuance providers, and global tunables.
 *
 * Invariants:
 * - Must always have at least one system administrator while {@code ACTIVE}
 *   (enforced by {@link #removeAdmin(MemberId)}).
 * - {@code defaultReservationTimeout} must be positive.
 * - {@code queueLoadThreshold} must be non-negative.
 */
@Entity
@Table(name = "platform_config")
public class Platform {

    // מכיוון שזה סינגלטון, נקבע לו ID קשיח ותמיד נשלוף אותו.
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private final Long id = 1L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "platform_system_admins",
            joinColumns = @JoinColumn(name = "platform_id")
    )
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "member_id", nullable = false))
    })
    private Set<MemberId> systemAdmins;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "platform_payment_providers",
            joinColumns = @JoinColumn(name = "platform_id")
    )
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "provider_id", nullable = false))
    })
    private Set<ProviderId> paymentProviders;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "platform_issuance_providers",
            joinColumns = @JoinColumn(name = "platform_id")
    )
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "provider_id", nullable = false))
    })
    private Set<ProviderId> issuanceProviders;

    @Column(name = "default_reservation_timeout")
    private Duration defaultReservationTimeout;

    @Column(name = "queue_load_threshold")
    private int queueLoadThreshold;

    @Version
    private Long version;

    // חובה עבור JPA
    protected Platform() {
        this.systemAdmins = ConcurrentHashMap.newKeySet();
        this.paymentProviders = ConcurrentHashMap.newKeySet();
        this.issuanceProviders = ConcurrentHashMap.newKeySet();
    }

    public Platform(MemberId initialAdmin,
                    Duration defaultReservationTimeout,
                    int queueLoadThreshold) {
        Objects.requireNonNull(initialAdmin, "initialAdmin must not be null");
        this.status = PlatformStatus.INITIALIZING;
        // ConcurrentHashMap.newKeySet() — thread-safe Set backed by a CHM.
        // Required because AdminService mutates these sets concurrently.
        this.systemAdmins = ConcurrentHashMap.newKeySet();
        this.systemAdmins.add(initialAdmin);
        this.paymentProviders = ConcurrentHashMap.newKeySet();
        this.issuanceProviders = ConcurrentHashMap.newKeySet();
        setDefaultReservationTimeout(defaultReservationTimeout);
        setQueueLoadThreshold(queueLoadThreshold);
    }

    public void activate() {
        if (status != PlatformStatus.INITIALIZING) {
            throw new IllegalStateException("Platform can only be activated from INITIALIZING (was " + status + ")");
        }
        this.status = PlatformStatus.ACTIVE;
    }

    public void shutdown() {
        this.status = PlatformStatus.SHUTDOWN;
    }

    public void addAdmin(MemberId admin) {
        Objects.requireNonNull(admin, "admin must not be null");
        systemAdmins.add(admin);
    }

    public synchronized void removeAdmin(MemberId admin) {
        Objects.requireNonNull(admin, "admin must not be null");
        if (!systemAdmins.contains(admin)) {
            return;
        }
        if (systemAdmins.size() == 1) {
            throw new IllegalStateException("Cannot remove the last system administrator");
        }
        systemAdmins.remove(admin);
    }

    public void addPaymentProvider(ProviderId provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        paymentProviders.add(provider);
    }

    public void removePaymentProvider(ProviderId provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        paymentProviders.remove(provider);
    }

    public void addIssuanceProvider(ProviderId provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        issuanceProviders.add(provider);
    }

    public void removeIssuanceProvider(ProviderId provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        issuanceProviders.remove(provider);
    }

    public void setDefaultReservationTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("defaultReservationTimeout must be positive");
        }
        this.defaultReservationTimeout = timeout;
    }

    public void setQueueLoadThreshold(int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("queueLoadThreshold must be non-negative");
        }
        this.queueLoadThreshold = threshold;
    }

    public boolean isAdmin(MemberId memberId) {
        return systemAdmins.contains(memberId);
    }

    public PlatformStatus getStatus() {
        return status;
    }

    public Set<MemberId> getSystemAdmins() {
        return Collections.unmodifiableSet(systemAdmins);
    }

    public Set<ProviderId> getPaymentProviders() {
        return Collections.unmodifiableSet(paymentProviders);
    }

    public Set<ProviderId> getIssuanceProviders() {
        return Collections.unmodifiableSet(issuanceProviders);
    }

    public Duration getDefaultReservationTimeout() {
        return defaultReservationTimeout;
    }

    public int getQueueLoadThreshold() {
        return queueLoadThreshold;
    }
}
