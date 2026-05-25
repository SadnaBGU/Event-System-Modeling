package com.eventsystem.application.member;

import com.eventsystem.application.appexceptions.MemberNotFoundException;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.PersonalDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Use cases: get/update member details (II.3.4), cancel account (II.6.2).
 *
 * All write operations require an authenticated {@code actor} {@link MemberId}.
 * In V1 a member may only act on themselves; cross-member operations are an admin concern.
 */
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final MemberRepository members;

    public MemberService(MemberRepository members) {
        this.members = members;
    }

    public MemberDto getDetails(MemberId actor, MemberId target) {
        requireSelf(actor, target);
        Member m = load(target);
        return MemberDto.from(m);
    }

    public MemberDto updateDetails(MemberId actor, MemberId target, UpdateMemberDetailsRequest req) {
        Objects.requireNonNull(req, "request must not be null");
        requireSelf(actor, target);

        Member m = load(target);
        PersonalDetails newDetails = new PersonalDetails(
                req.firstName(), req.lastName(), req.email(), req.dateOfBirth());
        m.updateDetails(newDetails); // domain enforces non-null + cancelled-guard
        members.save(m);
        log.info("Member updated own details memberId={}", target.value());
        return MemberDto.from(m);
    }

    public void cancelOwnAccount(MemberId actor, MemberId target) {
        requireSelf(actor, target);
        Member m = load(target);
        m.cancel();
        members.save(m);
        log.info("Member cancelled own account memberId={}", target.value());
    }

    private Member load(MemberId id) {
        return members.findById(id).orElseThrow(() -> new MemberNotFoundException(id));
    }

    private static void requireSelf(MemberId actor, MemberId target) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (!actor.equals(target)) {
            throw new SecurityException("Actor " + actor.value()
                    + " is not authorized to act on member " + target.value());
        }
    }
}
