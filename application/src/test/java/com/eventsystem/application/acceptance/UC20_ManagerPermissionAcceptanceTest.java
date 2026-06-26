package com.eventsystem.application.acceptance;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.event.EventService;
import com.eventsystem.application.event.ZoneService;
import com.eventsystem.application.venue.VenueManagementService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.event.EventDetails;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.venue.IVenueRepository;
import com.eventsystem.domain.venue.Venue;
import com.eventsystem.domain.venue.VenueId;
import com.eventsystem.domain.zone.IZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * REQ: PRD-15, TST-13
 * UC: UC20 - Perform Authorized Management Action
 * UAT: UAT-61, UAT-62
 *
 * Purpose:
 * A Manager with exactly one granted permission should be able to perform only
 * actions requiring that permission, and should be denied from all actions that
 * require a different permission.
 *
 * These tests intentionally expose the current bug:
 * - EventService already checks permissions.
 * - ZoneService and VenueManagementService currently do not receive actorId/companyId
 *   and therefore cannot enforce VENUE_CONFIGURATION.
 */
@ExtendWith(MockitoExtension.class)
class UC20_ManagerPermissionAcceptanceTest {

    @Mock
    private IEventRepository eventRepository;

    @Mock
    private IZoneRepository zoneRepository;

    @Mock
    private IVenueRepository venueRepository;

    @Mock
    private IMemberRepository memberRepository;

    @Mock
    private ICompanyPermissionServicePort permissionChecker;

    private EventService eventService;
    private ZoneService zoneService;
    private VenueManagementService venueManagementService;

    private MemberId managerId;
    private CompanyId companyId;
    private EventId eventId;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, permissionChecker);
        zoneService = new ZoneService(zoneRepository);
        venueManagementService = new VenueManagementService(venueRepository, memberRepository);

        managerId = MemberId.random();
        companyId = CompanyId.random();
        eventId = EventId.random();
    }

    // -------------------------------------------------------------------------
    // EVENT_INVENTORY_MANAGEMENT - existing correct behavior
    // -------------------------------------------------------------------------

    // REQ: PRD-15, TST-13, UC20, UAT-61
    @Test
    void managerWithEventInventoryManagementCanCreateDraftEvent() {
        grantOnly(Permission.EVENT_INVENTORY_MANAGEMENT);

        assertDoesNotThrow(() ->
                eventService.createDraft(managerId, companyId, validEventDetails())
        );

        verify(eventRepository).save(any());
    }

    // REQ: PRD-15, TST-13, UC20, UAT-62
    @ParameterizedTest(name = "manager with only {0} cannot create draft event")
    @EnumSource(
            value = Permission.class,
            names = {
                    "VENUE_CONFIGURATION",
                    "MODIFY_POLICIES",
                    "VIEW_PURCHASE_HISTORY",
                    "GENERATE_SALES_REPORT"
            }
    )
    void managerWithoutEventInventoryManagementCannotCreateDraftEvent(Permission grantedPermission) {
        grantOnly(grantedPermission);

        assertThrows(SecurityException.class, () ->
                eventService.createDraft(managerId, companyId, validEventDetails())
        );

        verify(eventRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // VENUE_CONFIGURATION - expected behavior, currently failing
    // -------------------------------------------------------------------------

    // REQ: PRD-15, TST-13, UC20, UAT-61
    //
    // This test documents the desired behavior after the fix.
    // Currently ZoneService has no actorId/companyId parameter, so the action succeeds
    // without checking that the manager has VENUE_CONFIGURATION.
    @Test
    void managerWithVenueConfigurationCanCreateStandingZone() {
        grantOnly(Permission.VENUE_CONFIGURATION);

        assertDoesNotThrow(() ->
                zoneService.createStandingZone(
                        eventId,
                        "Standing Zone",
                        Money.of(BigDecimal.valueOf(100), "ILS"),
                        100
                )
        );

        verify(zoneRepository).save(any());
    }

    // REQ: PRD-15, TST-13, UC20, UAT-62
    @ParameterizedTest(name = "manager with only {0} cannot create standing zone")
    @EnumSource(
            value = Permission.class,
            names = {
                    "EVENT_INVENTORY_MANAGEMENT",
                    "MODIFY_POLICIES",
                    "VIEW_PURCHASE_HISTORY",
                    "GENERATE_SALES_REPORT"
            }
    )
    void managerWithoutVenueConfigurationCannotCreateStandingZone(Permission grantedPermission) {
        grantOnly(grantedPermission);

        assertThrows(SecurityException.class, () ->
                zoneService.createStandingZone(
                        eventId,
                        "Standing Zone",
                        Money.of(BigDecimal.valueOf(100), "ILS"),
                        100
                )
        );

        verify(zoneRepository, never()).save(any());
    }

    // REQ: PRD-15, TST-13, UC20, UAT-62
    @ParameterizedTest(name = "manager with only {0} cannot create venue")
    @EnumSource(
            value = Permission.class,
            names = {
                    "EVENT_INVENTORY_MANAGEMENT",
                    "MODIFY_POLICIES",
                    "VIEW_PURCHASE_HISTORY",
                    "GENERATE_SALES_REPORT"
            }
    )
    void managerWithoutVenueConfigurationCannotCreateVenue(Permission grantedPermission) {
        grantOnly(grantedPermission);

        assertThrows(SecurityException.class, () ->
                venueManagementService.createVenue(companyId, "Main Venue")
        );

        verify(venueRepository, never()).save(any());
    }

    // REQ: PRD-15, TST-13, UC20, UAT-62
    @ParameterizedTest(name = "manager with only {0} cannot add standing zone to venue")
    @EnumSource(
            value = Permission.class,
            names = {
                    "EVENT_INVENTORY_MANAGEMENT",
                    "MODIFY_POLICIES",
                    "VIEW_PURCHASE_HISTORY",
                    "GENERATE_SALES_REPORT"
            }
    )
    void managerWithoutVenueConfigurationCannotAddStandingZoneToVenue(Permission grantedPermission) {
        grantOnly(grantedPermission);

        Venue venue = new Venue(VenueId.generate(), companyId, "Main Venue");
        when(venueRepository.findById(venue.getVenueId())).thenReturn(Optional.of(venue));

        assertThrows(SecurityException.class, () ->
                venueManagementService.addStandingZone(
                        venue.getVenueId(),
                        "Standing",
                        BigDecimal.valueOf(100),
                        "ILS",
                        100
                )
        );

        verify(venueRepository, never()).save(any());
    }



    private void grantOnly(Permission permission) {
        when(permissionChecker.canManageEvents(managerId, companyId))
                .thenReturn(permission == Permission.EVENT_INVENTORY_MANAGEMENT);

        when(permissionChecker.canManagePurchasePolicies(managerId, companyId))
                .thenReturn(permission == Permission.MODIFY_POLICIES);

        when(permissionChecker.canManageDiscountPolicies(managerId, companyId))
                .thenReturn(permission == Permission.MODIFY_POLICIES);
    }

    private EventDetails validEventDetails() {
        return new EventDetails(
                "Test Event",
                List.of(LocalDateTime.now().plusDays(30)),
                "Music",
                "Beer Sheva",
                "Valid event description"
        );
    }
}