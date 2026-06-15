package com.eventsystem.domain.company;

import com.eventsystem.domain.member.MemberId;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class OwnerNode {
    @JsonProperty("memberId")
    private final MemberId memberId;
    
    @JsonProperty("appointerId")
    private final MemberId appointerId;
    @JsonProperty("accepted")
    private boolean accepted = true;
    @JsonProperty("appointedOwners")
    private final List<OwnerNode> appointedOwners = new ArrayList<>();
    @JsonProperty("appointedManagers")
    private final List<ManagerNode> appointedManagers = new ArrayList<>();

    public OwnerNode(MemberId memberId, MemberId appointerId) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.appointerId = appointerId;
        this.accepted = true;
    }

    public OwnerNode(MemberId memberId, MemberId appointerId, boolean accepted) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.appointerId = appointerId;
        this.accepted = accepted;
    }
    @JsonCreator
    private OwnerNode(
            @JsonProperty("memberId") MemberId memberId,
            @JsonProperty("appointerId") MemberId appointerId,
            @JsonProperty("accepted") boolean accepted,
            @JsonProperty("appointedOwners") List<OwnerNode> appointedOwners,
            @JsonProperty("appointedManagers") List<ManagerNode> appointedManagers) {
        
        this.memberId = memberId;
        this.appointerId = appointerId;
        this.accepted = accepted;
        if (appointedOwners != null) this.appointedOwners.addAll(appointedOwners);
        if (appointedManagers != null) this.appointedManagers.addAll(appointedManagers);
    }

    public MemberId memberId() {
        return memberId;
    }

    public MemberId appointerId() {
        return appointerId;
    }

    public List<OwnerNode> appointedOwners() {
        return Collections.unmodifiableList(appointedOwners);
    }

    public List<ManagerNode> appointedManagers() {
        return Collections.unmodifiableList(appointedManagers);
    }

    void addOwner(OwnerNode ownerNode) {
        appointedOwners.add(ownerNode);
    }

    void accept() {
        this.accepted = true;
    }

    boolean isAccepted() {
        return accepted;
    }

    void addManager(ManagerNode managerNode) {
        appointedManagers.add(managerNode);
    }

    Optional<OwnerNode> removeOwner(MemberId ownerId) {
        for (int i = 0; i < appointedOwners.size(); i++) {
            if (appointedOwners.get(i).memberId().equals(ownerId)) {
                return Optional.of(appointedOwners.remove(i));
            }
        }
        return Optional.empty();
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
