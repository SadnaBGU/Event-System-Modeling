package com.eventsystem.domain.lottery;

import com.eventsystem.domain.member.MemberId;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class LotteryWinner {

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "member_id", nullable = false))
    })
    private MemberId memberId;

    @Column(name = "permission_code", nullable = false)
    private String permissionCode;

    @Column(name = "code_expiry", nullable = false)
    private Instant codeExpiry;

    protected LotteryWinner() {}

    public LotteryWinner(MemberId memberId, String permissionCode, Instant codeExpiry) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.permissionCode = Objects.requireNonNull(permissionCode, "permissionCode must not be null");
        this.codeExpiry = Objects.requireNonNull(codeExpiry, "codeExpiry must not be null");
        if (permissionCode.isBlank()) {
            throw new IllegalArgumentException("permissionCode must not be blank");
        }
    }

    public MemberId memberId() { return memberId; }
    public String permissionCode() { return permissionCode; }
    public Instant codeExpiry() { return codeExpiry; }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(codeExpiry);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LotteryWinner that = (LotteryWinner) o;
        return Objects.equals(memberId, that.memberId) &&
               Objects.equals(permissionCode, that.permissionCode) &&
               Objects.equals(codeExpiry, that.codeExpiry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, permissionCode, codeExpiry);
    }
}