package com.eventsystem.domain.company;

import com.eventsystem.domain.domainexceptions.CompanyDomainException;
import com.eventsystem.domain.member.MemberId;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class AppointmentTree {
    @JsonProperty("root")
    private final OwnerNode root;


    public AppointmentTree(MemberId founderId) {
        this.root = new OwnerNode(Objects.requireNonNull(founderId, "founderId must not be null"), null);
    }
    @JsonCreator
    private AppointmentTree(@JsonProperty("root") OwnerNode root) {
        this.root = root;
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
            if (current.memberId().equals(memberId) && current.isAccepted()) {
                return Optional.of(current);
            }
            queue.addAll(current.appointedOwners());
        }
        return Optional.empty();
    }

    public Optional<ManagerNode> findManager(MemberId memberId) {
        Objects.requireNonNull(memberId, "memberId must not be null");
        Deque<OwnerNode> ownerQueue = new ArrayDeque<>();
        ownerQueue.add(root);
        while (!ownerQueue.isEmpty()) {
            OwnerNode current = ownerQueue.removeFirst();
            // Search in managers directly under this owner
            Optional<ManagerNode> found = searchManagerInHierarchy(current, memberId);
            if (found.isPresent()) {
                return found;
            }
            ownerQueue.addAll(current.appointedOwners());
        }
        return Optional.empty();
    }

    private Optional<ManagerNode> searchManagerInHierarchy(OwnerNode ownerNode, MemberId targetId) {
        Deque<ManagerNode> managerQueue = new ArrayDeque<>();
        managerQueue.addAll(ownerNode.appointedManagers());
        while (!managerQueue.isEmpty()) {
            ManagerNode current = managerQueue.removeFirst();
            if (current.memberId().equals(targetId) && current.isAccepted()) {
                return Optional.of(current);
            }
            managerQueue.addAll(current.appointedManagers());
        }
        return Optional.empty();
    }

    public int countOwners() {
        return getOwnerSubTree(root.memberId()).size();
    }

    /** True if the given member is the founder (root owner) of this tree. */
    public boolean isFounder(MemberId memberId) {
        Objects.requireNonNull(memberId, "memberId must not be null");
        return root.memberId().equals(memberId);
    }

    /**
     * Admin-forced removal (UC21/UC22): unconditionally removes the member from the
     * tree as owner or manager if present. No authority check. The founder is never
     * removed (handle the orphaned-company case separately).
     */
    public void forceRemove(MemberId memberId) {
        Objects.requireNonNull(memberId, "memberId must not be null");
        if (root.memberId().equals(memberId)) {
            return;
        }
        if (removeOwnerFromTree(root, memberId).isPresent()) {
            return;
        }
        removeManagerFromTree(root, memberId);
    }

    public void appointOwner(MemberId appointerId, MemberId targetId) {
        OwnerNode appointer = findOwner(appointerId)
            .orElseThrow(() -> new CompanyDomainException("appointer is not an owner"));
        ensureNotAlreadyAppointed(targetId);
        appointer.addOwner(new OwnerNode(targetId, appointerId, false));
    }

    public void appointManager(MemberId ownerId, MemberId targetId, Set<Permission> permissions) {
        OwnerNode owner = findOwner(ownerId)
            .orElseThrow(() -> new CompanyDomainException("actor is not an owner"));
        ensureNotAlreadyAppointed(targetId);
        owner.addManager(new ManagerNode(targetId, ownerId, permissions, false));
    }

    public void appointManagerToManager(MemberId appointerId, MemberId targetId, Set<Permission> permissions) {
        ManagerNode appointer = findManager(appointerId)
            .orElseThrow(() -> new CompanyDomainException("appointer is not a manager"));
        ensureNotAlreadyAppointed(targetId);
        appointer.addManager(new ManagerNode(targetId, appointerId, permissions, false));
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
    @JsonIgnore
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
        // check any existing appointment including pending ones
        if (findAnyOwner(targetId).isPresent() || findAnyManager(targetId).isPresent()) {
            throw new CompanyDomainException("target already has an appointment in this company");
        }
    }

    private Optional<OwnerNode> findAnyOwner(MemberId memberId) {
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

    private Optional<ManagerNode> findAnyManager(MemberId memberId) {
        Objects.requireNonNull(memberId, "memberId must not be null");
        Deque<OwnerNode> ownerQueue = new ArrayDeque<>();
        ownerQueue.add(root);
        while (!ownerQueue.isEmpty()) {
            OwnerNode current = ownerQueue.removeFirst();
            // Search in managers directly under this owner
            Deque<ManagerNode> managerQueue = new ArrayDeque<>();
            managerQueue.addAll(current.appointedManagers());
            while (!managerQueue.isEmpty()) {
                ManagerNode m = managerQueue.removeFirst();
                if (m.memberId().equals(memberId)) {
                    return Optional.of(m);
                }
                managerQueue.addAll(m.appointedManagers());
            }
            ownerQueue.addAll(current.appointedOwners());
        }
        return Optional.empty();
    }

    public void acceptAppointment(MemberId targetId) {
        Objects.requireNonNull(targetId, "targetId must not be null");
        // Try owners first
        Optional<OwnerNode> owner = findAnyOwner(targetId);
        if (owner.isPresent()) {
            OwnerNode node = owner.get();
            if (node.isAccepted()) {
                throw new CompanyDomainException("appointment already accepted");
            }
            node.accept();
            return;
        }

        Optional<ManagerNode> manager = findAnyManager(targetId);
        if (manager.isPresent()) {
            ManagerNode node = manager.get();
            if (node.isAccepted()) {
                throw new CompanyDomainException("appointment already accepted");
            }
            node.accept();
            return;
        }

        throw new CompanyDomainException("no pending appointment found for target");
    }

    private Optional<OwnerNode> removeOwnerFromTree(OwnerNode current, MemberId targetId) {
        Optional<OwnerNode> removedDirect = current.removeOwner(targetId);
        if (removedDirect.isPresent()) {
            OwnerNode removedNode = removedDirect.get();
            // Reassign removed owner's children to current owner (the parent)
            for (OwnerNode child : removedNode.appointedOwners()) {
                current.addOwner(child);
            }
            return removedDirect;
        }

        for (OwnerNode child : new ArrayList<>(current.appointedOwners())) {
            Optional<OwnerNode> removed = removeOwnerFromTree(child, targetId);
            if (removed.isPresent()) {
                return removed;
            }
        }
        return Optional.empty();
    }

    private boolean removeManagerFromTree(OwnerNode current, MemberId managerId) {
        // Try to remove from owners' managers
        Optional<ManagerNode> removed = current.removeManager(managerId);
        if (removed.isPresent()) {
            ManagerNode removedNode = removed.get();
            // Reassign removed manager's child managers to current owner
            for (ManagerNode childManager : removedNode.appointedManagers()) {
                current.addManager(childManager);
            }
            return true;
        }

        // Search recursively in owner children and manager children
        for (OwnerNode childOwner : new ArrayList<>(current.appointedOwners())) {
            if (removeManagerFromTree(childOwner, managerId)) {
                return true;
            }
        }

        // Also search in manager hierarchy
        for (ManagerNode manager : new ArrayList<>(current.appointedManagers())) {
            if (removeManagerFromManagerTree(manager, managerId)) {
                return true;
            }
        }

        return false;
    }

    private boolean removeManagerFromManagerTree(ManagerNode current, MemberId managerId) {
        // Try to remove from manager's child managers
        Optional<ManagerNode> removed = current.removeManager(managerId);
        if (removed.isPresent()) {
            ManagerNode removedNode = removed.get();
            // Reassign removed manager's child managers to current manager (parent)
            for (ManagerNode childManager : removedNode.appointedManagers()) {
                current.addManager(childManager);
            }
            return true;
        }

        // Search recursively in child managers
        for (ManagerNode childManager : new ArrayList<>(current.appointedManagers())) {
            if (removeManagerFromManagerTree(childManager, managerId)) {
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
