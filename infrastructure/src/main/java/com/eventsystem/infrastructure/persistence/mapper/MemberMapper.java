package com.eventsystem.infrastructure.persistence.mapper;

import com.eventsystem.domain.member.*;
import com.eventsystem.infrastructure.persistence.entities.*;

import java.util.List;

public class MemberMapper {

    public static MemberEntity toEntity(Member m) {

        MemberEntity e = new MemberEntity();

        e.setMemberId(m.getMemberId().value());
        e.setUsername(m.getUsername());
        e.setStatus(m.getStatus());

        if (m.getHashedCredentials() != null) {
            e.setHashedCredentials(new HashedCredentialsEmbeddable(
                    m.getHashedCredentials().hash(),
                    m.getHashedCredentials().salt(),
                    m.getHashedCredentials().algorithm()
            ));
        }

        if (m.getPersonalDetails() != null) {
            var p = m.getPersonalDetails();
            e.setPersonalDetails(new PersonalDetailsEmbeddable(
                    p.firstName(),
                    p.lastName(),
                    p.email(),
                    p.dateOfBirth()
            ));
        }

        m.getSuspension().ifPresent(s ->
                e.setSuspension(new SuspensionEmbeddable(
                        s.suspendedAt(),
                        s.duration(),
                        s.reason()
                ))
        );

        e.setNotificationInbox(
                m.getNotificationInbox().stream()
                        .map(MemberMapper::toEntity)
                        .toList()
        );

        return e;
    }

    public static Member toDomain(MemberEntity e) {

        Member m = new Member(
                new MemberId(e.getMemberId())
        );

        // NOTE: simplified reconstruction (safe because domain allows stubs)
        if (e.getHashedCredentials() != null) {
            m.changeCredentials(new HashedCredentials(
                    e.getHashedCredentials().getHash(),
                    e.getHashedCredentials().getSalt(),
                    e.getHashedCredentials().getAlgorithm()
            ));
        }

        if (e.getPersonalDetails() != null) {
            var p = e.getPersonalDetails();
            m.updateDetails(new PersonalDetails(
                    p.getFirstName(),
                    p.getLastName(),
                    p.getEmail(),
                    p.getDateOfBirth()
            ));
        }

        e.getNotificationInbox()
                .forEach(n ->
                        m.addNotification(new Notification(
                                n.getNotificationId(),
                                n.getType(),
                                n.getContent(),
                                n.getCreatedAt()
                        ))
                );

        return m;
    }

    private static NotificationEntity toEntity(Notification n) {
        NotificationEntity e = new NotificationEntity();
        e.setNotificationId(n.getNotificationId());
        e.setType(n.getType());
        e.setContent(n.getContent());
        e.setCreatedAt(n.getCreatedAt());
        e.setDelivered(n.isDelivered());
        return e;
    }
}