package com.eventsystem.application.event;

import com.eventsystem.application.company.ProductionCompanyService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductionEventPermissionCheckerTest {

    private ProductionCompanyService productionCompanyService;
    private ProductionEventPermissionChecker checker;

    @BeforeEach
    void setUp() {
        productionCompanyService = mock(ProductionCompanyService.class);
        checker = new ProductionEventPermissionChecker(productionCompanyService);
    }

    @Test
    void canManageEvents_whenCompanyServiceGrantsPermission_returnsTrue() {
        String actorId = "member-1";
        String companyId = "company-1";

        when(productionCompanyService.hasPermission(
                new MemberId(actorId),
                new CompanyId(companyId),
                Permission.EVENT_INVENTORY_MANAGEMENT
        )).thenReturn(true);

        boolean result = checker.canManageEvents(actorId, companyId);

        assertThat(result).isTrue();

        verify(productionCompanyService).hasPermission(
                new MemberId(actorId),
                new CompanyId(companyId),
                Permission.EVENT_INVENTORY_MANAGEMENT
        );
    }

    @Test
    void canManageEvents_whenCompanyServiceDeniesPermission_returnsFalse() {
        String actorId = "member-1";
        String companyId = "company-1";

        when(productionCompanyService.hasPermission(
                new MemberId(actorId),
                new CompanyId(companyId),
                Permission.EVENT_INVENTORY_MANAGEMENT
        )).thenReturn(false);

        boolean result = checker.canManageEvents(actorId, companyId);

        assertThat(result).isFalse();

        verify(productionCompanyService).hasPermission(
                new MemberId(actorId),
                new CompanyId(companyId),
                Permission.EVENT_INVENTORY_MANAGEMENT
        );
    }

    @Test
    void canManageEvents_whenActorIdIsBlank_returnsFalseAndDoesNotCallService() {
        boolean result = checker.canManageEvents("   ", "company-1");

        assertThat(result).isFalse();
        verifyNoInteractions(productionCompanyService);
    }

    @Test
    void canManageEvents_whenCompanyIdIsBlank_returnsFalseAndDoesNotCallService() {
        boolean result = checker.canManageEvents("member-1", "   ");

        assertThat(result).isFalse();
        verifyNoInteractions(productionCompanyService);
    }

    @Test
    void canManageEvents_whenActorIdIsNull_throwsNullPointerException() {
        assertThatThrownBy(() -> checker.canManageEvents(null, "company-1"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("actorId must not be null");

        verifyNoInteractions(productionCompanyService);
    }

    @Test
    void canManageEvents_whenCompanyIdIsNull_throwsNullPointerException() {
        assertThatThrownBy(() -> checker.canManageEvents("member-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("companyId must not be null");

        verifyNoInteractions(productionCompanyService);
    }
}