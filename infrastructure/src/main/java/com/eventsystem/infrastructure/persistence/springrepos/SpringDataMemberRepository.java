package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpringDataMemberRepository extends JpaRepository<Member, MemberId> {

    // Derived query method to find a member by username
    Optional<Member> findByUsername(String username);

    // Derived query method to find a member by status
    Optional<Member> findByStatus(MemberStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.memberId = :id")
    Optional<Member> findByIdForUpdate(@Param("id") MemberId id);
    
}
