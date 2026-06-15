package com.eventsystem.infrastructure.persistence.entities;



import java.time.Instant;
import java.util.List;

import com.eventsystem.domain.member.MemberStatus;

import jakarta.persistence.*;

@Entity
@Table(name = "members")
public class MemberEntity {

    @Id
    private String memberId;

    private String username;

    @Embedded
    private HashedCredentialsEmbeddable hashedCredentials;

    @Embedded
    private PersonalDetailsEmbeddable personalDetails;

    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    @Embedded
    private SuspensionEmbeddable suspension;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "member_id")
    private List<NotificationEntity> notificationInbox;

    public MemberEntity() {}

    // getters/setters

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public HashedCredentialsEmbeddable getHashedCredentials() {
        return hashedCredentials;
    }

    public void setHashedCredentials(HashedCredentialsEmbeddable hashedCredentials) {
        this.hashedCredentials = hashedCredentials;
    }

    public PersonalDetailsEmbeddable getPersonalDetails() {
        return personalDetails;
    }

    public void setPersonalDetails(PersonalDetailsEmbeddable personalDetails) {
        this.personalDetails = personalDetails;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public void setStatus(MemberStatus status) {
        this.status = status;
    }

    public SuspensionEmbeddable getSuspension() {
        return suspension;
    }

    public void setSuspension(SuspensionEmbeddable suspension) {
        this.suspension = suspension;
    }

    public List<NotificationEntity> getNotificationInbox() {
        return notificationInbox;
    }

    public void setNotificationInbox(List<NotificationEntity> notificationInbox) {
        this.notificationInbox = notificationInbox;
    }
}