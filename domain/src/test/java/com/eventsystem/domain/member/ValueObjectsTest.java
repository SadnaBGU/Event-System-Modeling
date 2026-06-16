package com.eventsystem.domain.member;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

    @Test
    void memberIdGenerateProducesUniqueIds() {
        MemberId a = MemberId.generate();
        MemberId b = MemberId.generate();
        assertThat(a).isNotEqualTo(b);
        assertThat(a.value()).isNotBlank();
    }

    @Test
    void memberIdEqualityIsByValue() {
        assertThat(new MemberId("abc")).isEqualTo(new MemberId("abc"));
    }

    @Test
    void memberIdRejectsBlankAndNull() {
        assertThatThrownBy(() -> new MemberId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MemberId(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void personalDetailsRejectsBlankAndNull() {
        LocalDate dob = LocalDate.of(1990, 1, 1);
        assertThatThrownBy(() -> new PersonalDetails(dob, "e@x", null, "S"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PersonalDetails(dob, "e@x", " ", "S"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PersonalDetails(dob, "e@x", "J", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PersonalDetails(dob, "", "J", "S"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PersonalDetails(null, "e@x", "J", "S"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void hashedCredentialsRejectsBlankHashAndAlgorithm() {
        assertThatThrownBy(() -> new HashedCredentials("", "salt", "BCrypt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HashedCredentials("hash", "salt", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notificationCreateProducesUndeliveredNotificationWithIdAndTimestamp() {
        Notification n = Notification.create(NotificationType.PURCHASE_COMPLETED, "ok");
        assertThat(n.isDelivered()).isFalse();
        assertThat(n.getNotificationId()).isNotBlank();
        assertThat(n.getCreatedAt()).isNotNull();
        assertThat(n.getType()).isEqualTo(NotificationType.PURCHASE_COMPLETED);
        assertThat(n.getContent()).isEqualTo("ok");
    }
}
