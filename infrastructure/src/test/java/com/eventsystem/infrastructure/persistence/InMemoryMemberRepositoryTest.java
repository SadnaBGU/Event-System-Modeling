package com.eventsystem.infrastructure.persistence;

import com.eventsystem.domain.member.HashedCredentials;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.PersonalDetails;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryMemberRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryMemberRepositoryTest {

    private InMemoryMemberRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryMemberRepository();
    }

    private Member member(String username) {
        return new Member(
                MemberId.generate(),
                username,
                new HashedCredentials("h", "s", "BCrypt"),
                new PersonalDetails("F", "L", "e@x.com", LocalDate.of(1990, 1, 1)));
    }

    @Test
    void findByIdReturnsEmptyWhenAbsent() {
        assertThat(repo.findById(MemberId.generate())).isEmpty();
    }

    @Test
    void findByUsernameReturnsEmptyWhenAbsent() {
        assertThat(repo.findByUsername("nobody")).isEmpty();
    }

    @Test
    void saveThenFindById() {
        Member m = member("jon");
        repo.save(m);
        assertThat(repo.findById(m.getMemberId())).contains(m);
    }

    @Test
    void saveThenFindByUsername() {
        Member m = member("jon");
        repo.save(m);
        assertThat(repo.findByUsername("jon")).contains(m);
    }

    @Test
    void duplicateUsernameByDifferentIdIsRejected() {
        repo.save(member("jon"));
        assertThatThrownBy(() -> repo.save(member("jon")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jon");
    }

    @Test
    void resavingSameMemberIsAllowed() {
        Member m = member("jon");
        repo.save(m);
        repo.save(m);
        assertThat(repo.size()).isEqualTo(1);
    }

    @Test
    void clearRemovesAll() {
        repo.save(member("a"));
        repo.save(member("b"));
        repo.clear();
        assertThat(repo.size()).isZero();
        assertThat(repo.findByUsername("a")).isEmpty();
    }

    @Test
    void rejectsNullArgs() {
        assertThatThrownBy(() -> repo.findById(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.findByUsername(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.save(null)).isInstanceOf(NullPointerException.class);
    }
}
