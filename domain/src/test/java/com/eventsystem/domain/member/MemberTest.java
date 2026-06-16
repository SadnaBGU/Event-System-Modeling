package com.eventsystem.domain.member;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

    private static final HashedCredentials CREDS =
            new HashedCredentials("hash123", "salt456", "BCrypt");
    private static final PersonalDetails DETAILS = new PersonalDetails(LocalDate.of(1990, 1, 1), "jon@winterfell.north", 
            "Jon", "Snow");

    private Member newMember() {
        return new Member(MemberId.generate(), "jon", CREDS, DETAILS);
    }

    @Test
    void newMemberIsActiveAndHasEmptyInbox() {
        Member m = newMember();
        assertThat(m.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(m.getNotificationInbox()).isEmpty();
        assertThat(m.getUndeliveredNotifications()).isEmpty();
    }

    @Test
    void constructorRejectsNullArgs() {
        assertThatThrownBy(() -> new Member(null, "jon", CREDS, DETAILS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Member(MemberId.generate(), null, CREDS, DETAILS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Member(MemberId.generate(), "jon", null, DETAILS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Member(MemberId.generate(), "jon", CREDS, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRejectsBlankUsername() {
        assertThatThrownBy(() -> new Member(MemberId.generate(), "  ", CREDS, DETAILS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateDetailsReplacesPersonalDetails() {
        Member m = newMember();
        PersonalDetails updated = new PersonalDetails(LocalDate.of(1990, 1, 1), "aegon@dragon.fire", 
                "Aegon", "Targaryen");
        m.updateDetails(updated);
        assertThat(m.getPersonalDetails()).isEqualTo(updated);
    }

    @Test
    void changeCredentialsReplacesHash() {
        Member m = newMember();
        HashedCredentials newCreds = new HashedCredentials("h2", "s2", "BCrypt");
        m.changeCredentials(newCreds);
        assertThat(m.getHashedCredentials()).isEqualTo(newCreds);
    }

    @Test
    void addNotificationAppendsToInbox() {
        Member m = newMember();
        Notification n = Notification.create(NotificationType.PURCHASE_COMPLETED, "ok");
        m.addNotification(n);
        assertThat(m.getNotificationInbox()).containsExactly(n);
    }

    @Test
    void getUndeliveredFiltersDeliveredOnes() {
        Member m = newMember();
        Notification n1 = Notification.create(NotificationType.PURCHASE_COMPLETED, "first");
        Notification n2 = Notification.create(NotificationType.LOTTERY_WON, "second");
        m.addNotification(n1);
        m.addNotification(n2);

        assertThat(m.getUndeliveredNotifications()).containsExactly(n1, n2);

        m.markNotificationsDelivered();
        assertThat(m.getUndeliveredNotifications()).isEmpty();
        assertThat(m.getNotificationInbox()).hasSize(2);
        assertThat(n1.isDelivered()).isTrue();
        assertThat(n2.isDelivered()).isTrue();
    }

    @Test
    void cancelMarksMemberCancelled() {
        Member m = newMember();
        m.cancel();
        assertThat(m.getStatus()).isEqualTo(MemberStatus.CANCELLED);
    }

    @Test
    void cancelledMemberCannotBeMutated() {
        Member m = newMember();
        m.cancel();

        assertThatThrownBy(() -> m.updateDetails(DETAILS))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> m.changeCredentials(CREDS))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> m.addNotification(Notification.create(NotificationType.SOLD_OUT, "x")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void notificationInboxIsUnmodifiableView() {
        Member m = newMember();
        assertThatThrownBy(() -> m.getNotificationInbox()
                .add(Notification.create(NotificationType.SOLD_OUT, "x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equalityIsByMemberId() {
        MemberId id = MemberId.generate();
        Member a = new Member(id, "jon", CREDS, DETAILS);
        Member b = new Member(id, "OTHER_USERNAME", CREDS, DETAILS);
        Member c = new Member(MemberId.generate(), "jon", CREDS, DETAILS);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }
}
