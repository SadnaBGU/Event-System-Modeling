package com.eventsystem.domain.member;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate Root — a registered platform user.
 * Boundary: {@link Member} root + its {@link Notification} inbox.
 *
 * Invariants:
 * - {@code memberId}, {@code username}, {@code hashedCredentials}, {@code personalDetails} are non-null.
 * - {@code username} is immutable after creation.
 * - A {@link MemberStatus#CANCELLED} member cannot be modified or receive new notifications.
 * - A {@link MemberStatus#SUSPENDED} member cannot modify profile or credentials, but can receive
 *   notifications and perform read-only operations.
 */
public class Member {

    private final MemberId memberId;
    private final String username;
    private HashedCredentials hashedCredentials;
    private PersonalDetails personalDetails;
    private MemberStatus status;
    private Suspension suspension;
    private final List<Notification> notificationInbox;

    public Member(MemberId memberId,
                  String username,
                  HashedCredentials hashedCredentials,
                  PersonalDetails personalDetails) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        this.hashedCredentials = Objects.requireNonNull(hashedCredentials, "hashedCredentials must not be null");
        this.personalDetails = Objects.requireNonNull(personalDetails, "personalDetails must not be null");
        this.status = MemberStatus.ACTIVE;
        this.notificationInbox = new ArrayList<>();
    }

    /**
     * Convenience constructor for tests and stubs that only need an identity.
     * Creates a member with no credentials or personal details (stub state).
     */
    public Member(MemberId memberId) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.username = memberId.value();
        this.hashedCredentials = null;
        this.personalDetails = null;
        this.status = MemberStatus.ACTIVE;
        this.notificationInbox = new ArrayList<>();
    }

    public void updateDetails(PersonalDetails newDetails) {
        requireActive();
        this.personalDetails = Objects.requireNonNull(newDetails, "newDetails must not be null");
    }

    public void changeCredentials(HashedCredentials newCredentials) {
        requireActive();
        this.hashedCredentials = Objects.requireNonNull(newCredentials, "newCredentials must not be null");
    }

    public void addNotification(Notification notification) {
        requireNotCancelled();
        Objects.requireNonNull(notification, "notification must not be null");
        notificationInbox.add(notification);
    }

    public List<Notification> getUndeliveredNotifications() {
        List<Notification> undelivered = new ArrayList<>();
        for (Notification n : notificationInbox) {
            if (!n.isDelivered()) {
                undelivered.add(n);
            }
        }
        return Collections.unmodifiableList(undelivered);
    }

    public void markNotificationsDelivered() {
        for (Notification n : notificationInbox) {
            if (!n.isDelivered()) {
                n.markDelivered();
            }
        }
    }

    public void cancel() {
        this.status = MemberStatus.CANCELLED;
    }

    // ── Suspension (II.6.7 / II.6.8) ────────────────────────────────────────

    /**
     * Suspends this member.
     * @param now      current time (injected so domain stays clock-independent)
     * @param duration how long to suspend; {@code null} means permanent
     * @param reason the reason for the suspension (optional, may be null or empty)
     */
    public void suspend(Instant now, Duration duration, String reason) {
        Objects.requireNonNull(now, "now must not be null");
        if (status == MemberStatus.CANCELLED) {
            throw new IllegalStateException("Cannot suspend a cancelled member: " + username);
        }
        this.suspension = new Suspension(now, duration, reason);
        this.status = MemberStatus.SUSPENDED;
    }

    /** Lifts an active suspension, returning the member to ACTIVE status. */
    public void unsuspend() {
        if (status != MemberStatus.SUSPENDED) {
            throw new IllegalStateException("Member " + username + " is not suspended");
        }
        this.suspension = null;
        this.status = MemberStatus.ACTIVE;
    }

    /**
     * Returns true if the member is currently suspended at the given instant.
     * A temporary suspension that has already expired is treated as not suspended.
     */
    public boolean isSuspendedAt(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return status == MemberStatus.SUSPENDED && !suspension.isExpiredAt(now);
    }

    public Optional<Suspension> getSuspension() {
        return Optional.ofNullable(suspension);
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public MemberId getMemberId() {
        return memberId;
    }

    /** Alias for {@link #getMemberId()} — record-style accessor for compatibility. */
    public MemberId memberId() {
        return memberId;
    }

    public String getUsername() {
        return username;
    }

    public HashedCredentials getHashedCredentials() {
        return hashedCredentials;
    }

    public PersonalDetails getPersonalDetails() {
        return personalDetails;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public List<Notification> getNotificationInbox() {
        return Collections.unmodifiableList(notificationInbox);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member other)) return false;
        return memberId.equals(other.memberId);
    }

    @Override
    public int hashCode() {
        return memberId.hashCode();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void requireActive() {
        if (status == MemberStatus.CANCELLED) {
            throw new IllegalStateException("Member " + username + " is cancelled and cannot be modified");
        }
        if (status == MemberStatus.SUSPENDED) {
            throw new IllegalStateException("Member " + username + " is suspended and cannot perform this action");
        }
    }

    private void requireNotCancelled() {
        if (status == MemberStatus.CANCELLED) {
            throw new IllegalStateException("Member " + username + " is cancelled and cannot be modified");
        }
    }
}
