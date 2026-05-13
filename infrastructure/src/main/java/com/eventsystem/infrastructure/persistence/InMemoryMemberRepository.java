package com.eventsystem.infrastructure.persistence;

import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory adapter for {@link MemberRepository}.
 *
 * Backed by two {@link ConcurrentHashMap} instances (id-keyed + username-keyed).
 * Username uniqueness is enforced atomically via {@link ConcurrentMap#putIfAbsent}.
 */
public class InMemoryMemberRepository implements MemberRepository {

    private final ConcurrentMap<MemberId, Member> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MemberId> idByUsername = new ConcurrentHashMap<>();

    @Override
    public Optional<Member> findById(MemberId memberId) {
        Objects.requireNonNull(memberId, "memberId must not be null");
        return Optional.ofNullable(byId.get(memberId));
    }

    @Override
    public Optional<Member> findByUsername(String username) {
        Objects.requireNonNull(username, "username must not be null");
        MemberId id = idByUsername.get(username);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public void save(Member member) {
        Objects.requireNonNull(member, "member must not be null");

        // Atomic claim of the username on first save.
        MemberId existingForUsername = idByUsername.putIfAbsent(member.getUsername(), member.getMemberId());
        if (existingForUsername != null && !existingForUsername.equals(member.getMemberId())) {
            throw new IllegalStateException(
                    "Username already taken: " + member.getUsername());
        }
        byId.put(member.getMemberId(), member);
    }

    /** Test-support: clear all state. */
    public void clear() {
        byId.clear();
        idByUsername.clear();
    }

    /** Test-support: number of stored members. */
    public int size() {
        return byId.size();
    }
}
