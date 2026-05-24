package com.eventsystem.domain.company;

import com.eventsystem.domain.member.MemberId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ManagerNode {
    private final MemberId memberId;
    private final MemberId appointerId;
    private final Set<Permission> permissions;
    private boolean accepted = true;
    private final List<ManagerNode> appointedManagers = new ArrayList<>();

    public ManagerNode(MemberId memberId, MemberId appointerId, Set<Permission> permissions) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.appointerId = Objects.requireNonNull(appointerId, "appointerId must not be null");
        Objects.requireNonNull(permissions, "permissions must not be null");
        this.permissions = EnumSet.copyOf(permissions);
        this.accepted = true;
    }

    public ManagerNode(MemberId memberId, MemberId appointerId, Set<Permission> permissions, boolean accepted) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.appointerId = Objects.requireNonNull(appointerId, "appointerId must not be null");
        Objects.requireNonNull(permissions, "permissions must not be null");
        this.permissions = EnumSet.copyOf(permissions);
        this.accepted = accepted;
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

    public List<ManagerNode> appointedManagers() {
        return Collections.unmodifiableList(appointedManagers);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    public void replacePermissions(Set<Permission> newPermissions) {
        Objects.requireNonNull(newPermissions, "newPermissions must not be null");
        permissions.clear();
        permissions.addAll(newPermissions);
    }

    void addManager(ManagerNode managerNode) {
        appointedManagers.add(managerNode);
    }

    void accept() {
        this.accepted = true;
    }

    boolean isAccepted() {
        return accepted;
    }

    Optional<ManagerNode> removeManager(MemberId managerId) {
        for (int i = 0; i < appointedManagers.size(); i++) {
            if (appointedManagers.get(i).memberId().equals(managerId)) {
                return Optional.of(appointedManagers.remove(i));
            }
        }
        return Optional.empty();
    }
}

