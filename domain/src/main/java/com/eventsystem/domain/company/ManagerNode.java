package com.eventsystem.domain.company;

import com.eventsystem.domain.member.MemberId;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class ManagerNode {
    private final MemberId memberId;
    private final MemberId appointerId;
    private final Set<Permission> permissions;

    public ManagerNode(MemberId memberId, MemberId appointerId, Set<Permission> permissions) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.appointerId = Objects.requireNonNull(appointerId, "appointerId must not be null");
        Objects.requireNonNull(permissions, "permissions must not be null");
        this.permissions = EnumSet.copyOf(permissions);
    }

    public MemberId memberId() {
        return memberId;
    }

    public MemberId appointerId() {
        return appointerId;
    }

    public Set<Permission> permissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    public void replacePermissions(Set<Permission> newPermissions) {
        Objects.requireNonNull(newPermissions, "newPermissions must not be null");
        permissions.clear();
        permissions.addAll(newPermissions);
    }
}
