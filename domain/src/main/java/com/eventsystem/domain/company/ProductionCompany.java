package com.eventsystem.domain.company;

import com.eventsystem.domain.domainexceptions.CompanyDomainException;
import com.eventsystem.domain.member.MemberId;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "production_companies")
public final class ProductionCompany implements Persistable<CompanyId> {
    @EmbeddedId
    private CompanyId companyId;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "founder_id"))
    })
    private MemberId founderId;

    @Embedded
    private CompanyDetails companyDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompanyStatus status;

    // כאן הקסם קורה! כל העץ נשמר כ-JSONB בתוך עמודה אחת בפוסטגרס
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "appointment_tree", columnDefinition = "jsonb")
    private AppointmentTree appointmentTree;

    @Column(name = "name", unique = true, nullable = false)
    private String name;

    @Transient
    private boolean isNew = true;

    // --- מימוש פונקציות הממשק Persistable ---

    @Override
    public CompanyId getId() {
        return this.companyId;
    }

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    // פונקציות קסם של JPA שמכבות את הדגל ברגע שהאובייקט נטען מה-DB או נשמר אליו
    @PostPersist
    @PostLoad
    protected void markNotNew() {
        this.isNew = false;
    }

    protected ProductionCompany() {
    }

    private ProductionCompany(CompanyId companyId, MemberId founderId, CompanyDetails companyDetails) {
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.founderId = Objects.requireNonNull(founderId, "founderId must not be null");
        this.companyDetails = Objects.requireNonNull(companyDetails, "companyDetails must not be null");
        this.status = CompanyStatus.ACTIVE;
        this.name = companyDetails.name();
        this.appointmentTree = new AppointmentTree(founderId);
    }

    public static ProductionCompany create(MemberId founderId, String name, String description, double rating) {
        return new ProductionCompany(CompanyId.random(), founderId, new CompanyDetails(name, description, rating));
    }

    public synchronized void updateName(String newName) {
    requireActive();
    if (newName == null || newName.isBlank()) {
        throw new IllegalArgumentException("name must not be blank");
    }
    this.companyDetails = new CompanyDetails(newName, companyDetails.description(), companyDetails.rating());
    this.name = newName; // חובה כדי לסנכרן עם ה-DB!
}

    public synchronized void appointOwner(MemberId appointerId, MemberId targetId) {
        requireActive();
        appointmentTree.appointOwner(appointerId, targetId);
    }

    public synchronized void removeOwner(MemberId removerId, MemberId targetId) {
        requireActive();
        appointmentTree.removeOwner(removerId, targetId);
    }

    public synchronized void relinquishOwnership(MemberId memberId) {
        requireActive();
        if (founderId.equals(memberId)) {
            throw new CompanyDomainException("founder cannot relinquish ownership");
        }
        appointmentTree.removeOwner(memberId, memberId);
    }

    public synchronized void appointManager(MemberId ownerId, MemberId targetId, Set<Permission> permissions) {
        requireActive();
        appointmentTree.appointManager(ownerId, targetId, permissions);
    }

    public synchronized void appointManagerToManager(MemberId appointerId, MemberId targetId, Set<Permission> permissions) {
        requireActive();
        appointmentTree.appointManagerToManager(appointerId, targetId, permissions);
    }

    public synchronized void removeManager(MemberId ownerId, MemberId managerId) {
        requireActive();
        appointmentTree.removeManager(ownerId, managerId);
    }

    public synchronized void modifyManagerPermissions(MemberId ownerId, MemberId managerId, Set<Permission> permissions) {
        requireActive();
        appointmentTree.modifyManagerPermissions(ownerId, managerId, permissions);
    }

    public synchronized void suspend() {
        if (status == CompanyStatus.TERMINATED) {
            throw new CompanyDomainException("terminated company cannot be suspended");
        }
        status = CompanyStatus.SUSPENDED;
    }

    public synchronized void terminate() {
        status = CompanyStatus.TERMINATED;
    }

    public synchronized void adminClose() {
        status = CompanyStatus.ADMIN_CLOSED;
    }


    public synchronized void reopen() {
        if (status == CompanyStatus.TERMINATED) {
            throw new CompanyDomainException("terminated company cannot be reopened");
        }
        if (status == CompanyStatus.ADMIN_CLOSED) {
            throw new CompanyDomainException("admin-closed company cannot be reopened");
        }
        status = CompanyStatus.ACTIVE;
    }

    public synchronized boolean isOwner(MemberId memberId) {
        return appointmentTree.findOwner(memberId).isPresent();
    }

    public synchronized boolean isManager(MemberId memberId) {
        return appointmentTree.findManager(memberId).isPresent();
    }

    public synchronized boolean hasPermission(MemberId memberId, Permission permission) {
        Objects.requireNonNull(permission, "permission must not be null");
        if (status != CompanyStatus.ACTIVE) {
            return false;
        }
        if (isOwner(memberId)) {
            return true;
        }
        return appointmentTree.findManager(memberId)
                .map(managerNode -> managerNode.hasPermission(permission))
                .orElse(false);
    }

    public synchronized List<MemberId> getAppointmentSubTree(MemberId ownerId) {
        return appointmentTree.getAppointmentSubTree(ownerId);
    }

    /** Accept a pending owner/manager appointment (target must call this) */
    public synchronized void acceptAppointment(MemberId targetId) {
        requireActive();
        appointmentTree.acceptAppointment(targetId);
    }


    public synchronized void updateDescription(String newDescription) {
        requireActive();
        if (newDescription == null || newDescription.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        this.companyDetails = new CompanyDetails(companyDetails.name(), newDescription, companyDetails.rating());
    }

    public synchronized void updateRating(double newRating) {
        requireActive();
        if (newRating < 0.0 || newRating > 5.0) {
            throw new IllegalArgumentException("rating must be in range [0, 5]");
        }
        this.companyDetails = new CompanyDetails(companyDetails.name(), companyDetails.description(), newRating);
    }

    public CompanyId companyId() {
        return companyId;
    }

    public CompanyDetails companyDetails() {
        return companyDetails;
    }

    public CompanyStatus status() {
        return status;
    }

    public MemberId founderId() {
        return founderId;
    }

    private void requireActive() {
        if (status != CompanyStatus.ACTIVE) {
            throw new CompanyDomainException("company is not active");
        }
    }
}
