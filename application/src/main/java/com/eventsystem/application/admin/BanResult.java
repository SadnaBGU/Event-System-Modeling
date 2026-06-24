package com.eventsystem.application.admin;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.member.MemberId;

import java.util.List;

/**
 * Outcome of an admin ban (UC22).
 *
 * @param bannedMember       the member whose account was disabled
 * @param orphanedCompanies  companies of which the banned member was the sole founder;
 *                           their founder role is NOT auto-revoked and the admin is
 *                           warned to reassign a founder or close the company.
 */
public record BanResult(MemberId bannedMember, List<CompanyId> orphanedCompanies) {

    public BanResult {
        orphanedCompanies = List.copyOf(orphanedCompanies);
    }

    public boolean hasOrphanedCompanies() {
        return !orphanedCompanies.isEmpty();
    }
}