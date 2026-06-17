package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.member.HashedCredentials;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.Notification;
import com.eventsystem.domain.member.NotificationType;
import com.eventsystem.domain.member.PersonalDetails;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresMemberRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresMemberRepository.class)
class PostgresMemberRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresMemberRepository memberRepository;

    @Autowired
    private EntityManager em;

    @Test
    void saveAndFindById_persistsMemberAndNotifications() {
        Member member = member("member-1", "alice", "alice@example.test");
        member.addNotification(Notification.create(NotificationType.PURCHASE_COMPLETED, "ticket ready"));

        memberRepository.save(member);
        em.flush();
        em.clear();

        Member found = memberRepository.findById(new MemberId("member-1")).orElseThrow();

        assertThat(found.getUsername()).isEqualTo("alice");
        assertThat(found.getPersonalDetails().email()).isEqualTo("alice@example.test");
        assertThat(found.getUndeliveredNotifications())
                .extracting(Notification::getContent)
                .containsExactly("ticket ready");
    }

    @Test
    void findById_returnsEmptyForMissingMember() {
        assertThat(memberRepository.findById(new MemberId("missing-member"))).isEmpty();
    }

    @Test
    void findByUsername_loadsPersistedMember() {
        Member member = member("member-username", "carol", "carol@example.test");
        memberRepository.save(member);
        em.flush();
        em.clear();

        Member found = memberRepository.findByUsername("carol").orElseThrow();

        assertThat(found.getMemberId()).isEqualTo(new MemberId("member-username"));
        assertThat(found.getPersonalDetails().email()).isEqualTo("carol@example.test");
    }

    @Test
    void update_persistsChangedPersonalDetails() {
        Member member = member("member-2", "bob", "bob@example.test");
        memberRepository.save(member);
        em.flush();
        em.clear();

        Member found = memberRepository.findById(new MemberId("member-2")).orElseThrow();
        found.updateDetails(new PersonalDetails(LocalDate.of(1991, 2, 3), "new-bob@example.test", "Robert", "Builder"));
        memberRepository.save(found);
        em.flush();
        em.clear();

        Member updated = memberRepository.findById(new MemberId("member-2")).orElseThrow();

        assertThat(updated.getPersonalDetails().email()).isEqualTo("new-bob@example.test");
        assertThat(updated.getPersonalDetails().firstName()).isEqualTo("Robert");
        assertThat(updated.getPersonalDetails().lastName()).isEqualTo("Builder");
    }

    @Test
    void update_persistsNotificationDeliveryState() {
        Member member = member("member-notifications", "dana", "dana@example.test");
        member.addNotification(Notification.create(NotificationType.PURCHASE_COMPLETED, "first"));
        member.addNotification(Notification.create(NotificationType.EVENT_CANCELLED, "second"));
        memberRepository.save(member);
        em.flush();
        em.clear();

        Member found = memberRepository.findById(new MemberId("member-notifications")).orElseThrow();
        found.markNotificationsDelivered();
        memberRepository.save(found);
        em.flush();
        em.clear();

        Member updated = memberRepository.findById(new MemberId("member-notifications")).orElseThrow();

        assertThat(updated.getUndeliveredNotifications()).isEmpty();
        assertThat(updated.getNotificationInbox())
                .extracting(Notification::isDelivered)
                .containsExactly(true, true);
    }

    private static Member member(String id, String username, String email) {
        return new Member(
                new MemberId(id),
                username,
                new HashedCredentials("hash-" + id, "salt-" + id, "bcrypt"),
                new PersonalDetails(LocalDate.of(1990, 1, 1), email, "Test", "User")
        );
    }
}
