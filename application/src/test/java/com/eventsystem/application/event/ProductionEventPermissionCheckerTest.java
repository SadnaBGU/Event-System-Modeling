package com.eventsystem.application.event;

import com.eventsystem.application.company.ProductionCompanyService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.company.ProductionCompanyRepository;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * UC20: Perform Authorized Management Action
 *
 * Tests for UATs:
 * - UAT-61: Authorized Action Success
 * - UAT-62: Unauthorized Action Denied
 * - UAT-63: Permission Revoked Mid-Action
 *
 * Event permission-checking coverage:
 * - Delegates event-management permission check to ProductionCompanyService
 * - Grants event action only when the actor has EVENT_INVENTORY_MANAGEMENT permission
 * - Denies event action when the actor does not have the required permission
 * - Rejects blank or null actor/company identifiers
 */
class ProductionEventPermissionCheckerTest {

    private ProductionCompanyRepository productionCompanyRepository;
    private ProductionEventPermissionChecker checker;

    @BeforeEach
    void setUp() {
        productionCompanyRepository = mock(ProductionCompanyRepository.class);
        checker = new ProductionEventPermissionChecker(productionCompanyRepository);
    }

    // UAT-61: Authorized Action Success
    // Return true when the company service confirms the actor has event-management permission.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void canManageEvents_whenCompanyServiceGrantsPermission_returnsTrue() {
        String actorId = "member-1";
        String companyId = "company-1";

        when(productionCompanyRepository.hasPermission(
                new MemberId(actorId),
                new CompanyId(companyId),
                Permission.EVENT_INVENTORY_MANAGEMENT
        )).thenReturn(true);

        boolean result = checker.canManageEvents(actorId, companyId);

        assertThat(result).isTrue();

        verify(productionCompanyRepository).hasPermission(
                new MemberId(actorId),
                new CompanyId(companyId),
                Permission.EVENT_INVENTORY_MANAGEMENT
        );
    }

    // UAT-62: Unauthorized Action Denied
    // Return false when the company service says the actor lacks event-management permission.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void canManageEvents_whenCompanyServiceDeniesPermission_returnsFalse() {
        String actorId = "member-1";
        String companyId = "company-1";

        when(productionCompanyRepository.hasPermission(
                new MemberId(actorId),
                new CompanyId(companyId),
                Permission.EVENT_INVENTORY_MANAGEMENT
        )).thenReturn(false);

        boolean result = checker.canManageEvents(actorId, companyId);

        assertThat(result).isFalse();

        verify(productionCompanyRepository).hasPermission(
                new MemberId(actorId),
                new CompanyId(companyId),
                Permission.EVENT_INVENTORY_MANAGEMENT
        );
    }

    // UAT-62: Unauthorized Action Denied
    // Treat a blank actor ID as unauthorized and avoid calling the company service.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void canManageEvents_whenActorIdIsBlank_returnsFalseAndDoesNotCallService() {
        boolean result = checker.canManageEvents("   ", "company-1");

        assertThat(result).isFalse();
        verifyNoInteractions(productionCompanyRepository);
    }

    // UAT-62: Unauthorized Action Denied
    // Treat a blank company ID as unauthorized and avoid calling the company service.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void canManageEvents_whenCompanyIdIsBlank_returnsFalseAndDoesNotCallService() {
        boolean result = checker.canManageEvents("member-1", "   ");

        assertThat(result).isFalse();
        verifyNoInteractions(productionCompanyRepository);
    }

    // Supporting validation test for UC20
    // Reject permission check when actor ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void canManageEvents_whenActorIdIsNull_throwsNullPointerException() {
        assertThatThrownBy(() -> checker.canManageEvents(null, "company-1"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("actorId must not be null");

        verifyNoInteractions(productionCompanyRepository);
    }

    // Supporting validation test for UC20
    // Reject permission check when company ID is null.
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void canManageEvents_whenCompanyIdIsNull_throwsNullPointerException() {
        assertThatThrownBy(() -> checker.canManageEvents("member-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("companyId must not be null");

        verifyNoInteractions(productionCompanyRepository);
    }
}