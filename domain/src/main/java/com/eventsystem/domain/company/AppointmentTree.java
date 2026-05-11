package com.eventsystem.domain.company;

import com.eventsystem.domain.member.MemberId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class AppointmentTree {
    private final OwnerNode root;

    public AppointmentTree(MemberId founderId) {
        this.root = new OwnerNode(Objects.requireNonNull(founderId, "founderId must not be null"), null);
    }

    public OwnerNode root() {
        return root;
    }

    public Optional<OwnerNode> findOwner(MemberId memberId) {
        Objects.requireNonNull(memberId, "memberId must not be null");
        Deque<OwnerNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            OwnerNode current = queue.removeFirst();
            if (current.memberId().equals(memberId)) {
                return Optional.of(current);
            }
            queue.addAll(current.appointedOwners());
        }
        return Optional.empty();
    }

    public Optional<ManagerNode> findManager(MemberId memberId) {
        Objects.requireNonNull(memberId, "memberId must not be null");
        Deque<OwnerNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            OwnerNode current = queue.removeFirst();
            for (ManagerNode manager : current.appointedManagers()) {
                if (manager.memberId().equals(memberId)) {
                    return Optional.of(manager);
                }
            }
            queue.addAll(current.appointedOwners());
        }
        return Optional.empty();
    }

    public int countOwners() {
        return getOwnerSubTree(root.memberId()).size();
    }

    public void appointOwner(MemberId appointerId, MemberId targetId) {
        OwnerNode appointer = findOwner(appointerId)
                .orElseThrow(() -> new CompanyDomainException("appointer is not an owner"));
        ensureNotAlreadyAppointed(targetId);
        appointer.addOwner(new OwnerNode(targetId, appointerId));
    }

    public void appointManager(MemberId ownerId, MemberId targetId, Set<Permission> permissions) {
        OwnerNode owner = findOwner(ownerId)
                .orElseThrow(() -> new CompanyDomainException("actor is not an owner"));
        ensureNotAlreadyAppointed(targetId);
        owner.addManager(new ManagerNode(targetId, ownerId, permissions));
    }

    public void removeOwner(MemberId removerId, MemberId targetId) {
        if (root.memberId().equals(targetId)) {
            throw new CompanyDomainException("cannot remove the founder owner");
        }
        OwnerNode remover = findOwner(removerId)
                .orElseThrow(() -> new CompanyDomainException("actor is not an owner"));
        if (!isInOwnerSubTree(remover.memberId(), targetId)) {
            throw new CompanyDomainException("target owner is not in remover subtree");
        }
        removeOwnerFromTree(root, targetId)
                .orElseThrow(() -> new CompanyDomainException("target owner not found"));
    }

    public void removeManager(MemberId removerId, MemberId managerId) {
        OwnerNode remover = findOwner(removerId)
                .orElseThrow(() -> new CompanyDomainException("actor is not an owner"));
        if (!isInOwnerSubTree(remover.memberId(), managerId) && !isManagerInOwnerSubTree(remover.memberId(), managerId)) {
            throw new CompanyDomainException("target manager is not in remover subtree");
        }

        if (!removeManagerFromTree(root, managerId)) {
            throw new CompanyDomainException("target manager not found");
        }
    }

    public void modifyManagerPermissions(MemberId ownerId, MemberId managerId, Set<Permission> permissions) {
        if (!isManagerInOwnerSubTree(ownerId, managerId)) {
            throw new CompanyDomainException("manager is not in owner subtree");
        }
        ManagerNode managerNode = findManager(managerId)
                .orElseThrow(() -> new CompanyDomainException("manager not found"));
        managerNode.replacePermissions(permissions);
    }

    public List<MemberId> getAppointmentSubTree(MemberId ownerId) {
        OwnerNode ownerNode = findOwner(ownerId)
                .orElseThrow(() -> new CompanyDomainException("owner not found"));
        List<MemberId> result = new ArrayList<>();
        collectIds(ownerNode, result);
        return List.copyOf(result);
    }

    public Set<MemberId> getAllMemberIds() {
        Set<MemberId> ids = new LinkedHashSet<>();
        collectIds(root, new ArrayList<>()).forEach(ids::add);
        return Set.copyOf(ids);
    }

    public boolean isManagerInOwnerSubTree(MemberId ownerId, MemberId managerId) {
        OwnerNode ownerNode = findOwner(ownerId)
                .orElseThrow(() -> new CompanyDomainException("owner not found"));
        Deque<OwnerNode> queue = new ArrayDeque<>();
        queue.add(ownerNode);
        while (!queue.isEmpty()) {
            OwnerNode current = queue.removeFirst();
            for (ManagerNode manager : current.appointedManagers()) {
                if (manager.memberId().equals(managerId)) {
                    return true;
                }
            }
            queue.addAll(current.appointedOwners());
        }
        return false;
    }

    private List<MemberId> getOwnerSubTree(MemberId ownerId) {
        OwnerNode ownerNode = findOwner(ownerId)
                .orElseThrow(() -> new CompanyDomainException("owner not found"));
        List<MemberId> result = new ArrayList<>();
        Deque<OwnerNode> queue = new ArrayDeque<>();
        queue.add(ownerNode);
        while (!queue.isEmpty()) {
            OwnerNode current = queue.removeFirst();
            result.add(current.memberId());
            queue.addAll(current.appointedOwners());
        }
        return result;
    }

    private boolean isInOwnerSubTree(MemberId ownerId, MemberId targetOwnerId) {
        return getOwnerSubTree(ownerId).contains(targetOwnerId);
    }

    private void ensureNotAlreadyAppointed(MemberId targetId) {
        if (findOwner(targetId).isPresent() || findManager(targetId).isPresent()) {
            throw new CompanyDomainException("target already has an appointment in this company");
        }
    }

    private Optional<OwnerNode> removeOwnerFromTree(OwnerNode current, MemberId targetId) {
        Optional<OwnerNode> removedDirect = current.removeOwner(targetId);
        if (removedDirect.isPresent()) {
            return removedDirect;
        }

        for (OwnerNode child : current.appointedOwners()) {
            Optional<OwnerNode> removed = removeOwnerFromTree(child, targetId);
            if (removed.isPresent()) {
                return removed;
            }
        }
        return Optional.empty();
    }

    private boolean removeManagerFromTree(OwnerNode current, MemberId managerId) {
        if (current.removeManager(managerId).isPresent()) {
            return true;
        }
        for (OwnerNode child : current.appointedOwners()) {
            if (removeManagerFromTree(child, managerId)) {
                return true;
            }
        }
        return false;
    }

    private List<MemberId> collectIds(OwnerNode ownerNode, List<MemberId> into) {
        into.add(ownerNode.memberId());
        for (ManagerNode manager : ownerNode.appointedManagers()) {
            into.add(manager.memberId());
        }
        for (OwnerNode childOwner : ownerNode.appointedOwners()) {
            collectIds(childOwner, into);
        }
        return into;
    }
}
