package com.eventsystem.domain.member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class MemberSuspensionTest {

    private Member member;

    @BeforeEach
    void setUp() {
        member = new Member(MemberId.random());
    }

    // ── suspend (II.6.7) ─────────────────────────────────────────────────────

    @Test
    void suspend_temporary_setsStatusAndRecordsSuspension() {
        Instant now = Instant.now();
        member.suspend(now, Duration.ofDays(7), "Violation of terms");

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspension()).isPresent();
        assertThat(member.getSuspension().get().isPermanent()).isFalse();
        assertThat(member.getSuspension().get().endsAt()).isEqualTo(now.plus(Duration.ofDays(7)));
    }

    @Test
    void suspend_permanent_nullDuration_setsStatusPermanent() {
        member.suspend(Instant.now(), null, "Permanent suspension");

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspension().get().isPermanent()).isTrue();
        assertThat(member.getSuspension().get().endsAt()).isNull();
    }

    @Test
    void suspend_cancelledMember_throws() {
        member.cancel();
        assertThatIllegalStateException()
                .isThrownBy(() -> member.suspend(Instant.now(), Duration.ofDays(1), "Violation of terms"));
    }

    @Test
    void isSuspendedAt_duringActiveSuspension_returnsTrue() {
        Instant now = Instant.now();
        member.suspend(now, Duration.ofDays(7), "Violation of terms");

        assertThat(member.isSuspendedAt(now.plusSeconds(60))).isTrue();
    }

    @Test
    void isSuspendedAt_afterTemporaryExpiry_returnsFalse() {
        Instant now = Instant.now();
        member.suspend(now, Duration.ofDays(1), "Violation of terms");

        assertThat(member.isSuspendedAt(now.plus(Duration.ofDays(2)))).isFalse();
    }

    @Test
    void isSuspendedAt_permanent_neverExpires() {
        Instant now = Instant.now();
        member.suspend(now, null, "Permanent suspension");

        assertThat(member.isSuspendedAt(now.plus(Duration.ofDays(3650)))).isTrue();
    }

    @Test
    void suspendedMember_cannotUpdateDetails() {
        member.suspend(Instant.now(), Duration.ofDays(1), "Violation of terms");
        assertThatIllegalStateException()
                .isThrownBy(() -> member.updateDetails(
                        new PersonalDetails(LocalDate.of(1990, 1, 1), "new@example.com", "New", "Name")));
    }

    @Test
    void suspendedMember_canReceiveNotifications() {
        member.suspend(Instant.now(), Duration.ofDays(1), "Violation of terms");
        assertThatNoException()
                .isThrownBy(() -> member.addNotification(
                        Notification.create(NotificationType.ROLE_CHANGED, "Your account has been suspended.")));
    }

    // ── unsuspend (II.6.8) ───────────────────────────────────────────────────

    @Test
    void unsuspend_restoresActiveStatus() {
        member.suspend(Instant.now(), Duration.ofDays(7), "Violation of terms");
        member.unsuspend();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getSuspension()).isEmpty();
    }

    @Test
    void unsuspend_notSuspended_throws() {
        assertThatIllegalStateException()
                .isThrownBy(member::unsuspend);
    }

    @Test
    void unsuspend_permanent_works() {
        member.suspend(Instant.now(), null, "Permanent suspension");
        member.unsuspend();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    // ── Suspension record ────────────────────────────────────────────────────

    @Test
    void suspension_zeroDuration_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suspension(Instant.now(), Duration.ZERO, "Invalid duration"));
    }

    @Test
    void suspension_negativeDuration_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suspension(Instant.now(), Duration.ofDays(-1), "Invalid duration"));
    }


    // ensure status changes when after suspension time expires
    @Test
    void isSuspendedAt_afterTemporarySuspensionExpired_memberStatusRefreshesToActive() {
        Instant startedAt = Instant.parse("2020-10-10T10:00:00Z");
        Instant afterExpiry = startedAt.plus(Duration.ofHours(2));

        member.suspend(startedAt, Duration.ofHours(1), "Temporary suspension");

        boolean suspended = member.isSuspendedAt(afterExpiry);

        assertThat(suspended).isFalse();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }
}
