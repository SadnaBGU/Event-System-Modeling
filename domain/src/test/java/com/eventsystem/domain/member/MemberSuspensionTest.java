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
        member.suspend(now, Duration.ofDays(7));

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspension()).isPresent();
        assertThat(member.getSuspension().get().isPermanent()).isFalse();
        assertThat(member.getSuspension().get().endsAt()).isEqualTo(now.plus(Duration.ofDays(7)));
    }

    @Test
    void suspend_permanent_nullDuration_setsStatusPermanent() {
        member.suspend(Instant.now(), null);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspension().get().isPermanent()).isTrue();
        assertThat(member.getSuspension().get().endsAt()).isNull();
    }

    @Test
    void suspend_cancelledMember_throws() {
        member.cancel();
        assertThatIllegalStateException()
                .isThrownBy(() -> member.suspend(Instant.now(), Duration.ofDays(1)));
    }

    @Test
    void isSuspendedAt_duringActiveSuspension_returnsTrue() {
        Instant now = Instant.now();
        member.suspend(now, Duration.ofDays(7));

        assertThat(member.isSuspendedAt(now.plusSeconds(60))).isTrue();
    }

    @Test
    void isSuspendedAt_afterTemporaryExpiry_returnsFalse() {
        Instant now = Instant.now();
        member.suspend(now, Duration.ofDays(1));

        assertThat(member.isSuspendedAt(now.plus(Duration.ofDays(2)))).isFalse();
    }

    @Test
    void isSuspendedAt_permanent_neverExpires() {
        Instant now = Instant.now();
        member.suspend(now, null);

        assertThat(member.isSuspendedAt(now.plus(Duration.ofDays(3650)))).isTrue();
    }

    @Test
    void suspendedMember_cannotUpdateDetails() {
        member.suspend(Instant.now(), Duration.ofDays(1));
        assertThatIllegalStateException()
                .isThrownBy(() -> member.updateDetails(
                        new PersonalDetails("New", "Name", "new@example.com", LocalDate.of(1990, 1, 1))));
    }

    @Test
    void suspendedMember_canReceiveNotifications() {
        member.suspend(Instant.now(), Duration.ofDays(1));
        assertThatNoException()
                .isThrownBy(() -> member.addNotification(
                        Notification.create(NotificationType.ROLE_CHANGED, "Your account has been suspended.")));
    }

    // ── unsuspend (II.6.8) ───────────────────────────────────────────────────

    @Test
    void unsuspend_restoresActiveStatus() {
        member.suspend(Instant.now(), Duration.ofDays(7));
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
        member.suspend(Instant.now(), null);
        member.unsuspend();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    // ── Suspension record ────────────────────────────────────────────────────

    @Test
    void suspension_zeroDuration_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suspension(Instant.now(), Duration.ZERO));
    }

    @Test
    void suspension_negativeDuration_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Suspension(Instant.now(), Duration.ofDays(-1)));
    }
}
