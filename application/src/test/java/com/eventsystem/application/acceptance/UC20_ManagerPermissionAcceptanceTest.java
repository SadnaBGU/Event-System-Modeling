package com.eventsystem.application.acceptance;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.event.EventService;
import com.eventsystem.application.event.IEventManagementPort;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * REQ: PRD-15, TST-13
 * UC: UC20 - Perform Authorized Management Action
 * UAT: UAT-61, UAT-62
 *
 * Purpose:
 * A Manager with exactly one granted permission should be able to perform only
 * actions covered by that permission, and should be denied from actions that
 * require a different permission.
 *
 * This is an application-level acceptance test:
 * it calls Application services directly, not controllers/UI and not Domain objects directly.
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

    @Mock
    private IEventManagementPort eventOwnershipChecker;

    private EventService eventService;
    private ZoneService zoneService;
    private VenueManagementService venueManagementService;

    private MemberId managerId;
    private CompanyId companyId;
    private EventId eventId;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, permissionChecker);

        zoneService = new ZoneService(
                zoneRepository,
                permissionChecker,
                eventOwnershipChecker
        );

        venueManagementService = new VenueManagementService(
                venueRepository,
                memberRepository,
                permissionChecker
        );

        managerId = MemberId.random();
        companyId = CompanyId.random();
        eventId = EventId.random();

        lenient().when(eventOwnershipChecker.companyOfEvent(eventId))
                .thenReturn(companyId);
    }

    // -------------------------------------------------------------------------
    // EVENT_INVENTORY_MANAGEMENT
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
    // ZONE CONFIGURATION
    // Current design:
    // ZoneService allows either VENUE_CONFIGURATION or EVENT_INVENTORY_MANAGEMENT.
    // -------------------------------------------------------------------------

    // REQ: PRD-15, TST-13, UC20, UAT-61
    @Test
    void managerWithVenueConfigurationCanCreateStandingZone() {
        grantOnly(Permission.VENUE_CONFIGURATION);

        assertDoesNotThrow(() ->
                zoneService.createStandingZone(
                        managerId,
                        eventId,
                        "Standing Zone",
                        Money.of(BigDecimal.valueOf(100), "ILS"),
                        100
                )
        );

        verify(zoneRepository).save(any());
    }

    // REQ: PRD-15, TST-13, UC20, UAT-61
    @Test
    void managerWithEventInventoryManagementCanCreateStandingZone() {
        grantOnly(Permission.EVENT_INVENTORY_MANAGEMENT);

        assertDoesNotThrow(() ->
                zoneService.createStandingZone(
                        managerId,
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
                    "MODIFY_POLICIES",
                    "VIEW_PURCHASE_HISTORY",
                    "GENERATE_SALES_REPORT"
            }
    )
    void managerWithoutVenueOrEventManagementCannotCreateStandingZone(Permission grantedPermission) {
        grantOnly(grantedPermission);

        assertThrows(SecurityException.class, () ->
                zoneService.createStandingZone(
                        managerId,
                        eventId,
                        "Standing Zone",
                        Money.of(BigDecimal.valueOf(100), "ILS"),
                        100
                )
        );

        verify(zoneRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // VENUE CONFIGURATION
    // Current design:
    // VenueManagementService allows only VENUE_CONFIGURATION.
    // -------------------------------------------------------------------------

    // REQ: PRD-15, TST-13, UC20, UAT-61
    @Test
    void managerWithVenueConfigurationCanCreateVenue() {
        grantOnly(Permission.VENUE_CONFIGURATION);

        assertDoesNotThrow(() ->
                venueManagementService.createVenue(managerId, companyId, "Main Venue")
        );

        verify(venueRepository).save(any());
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
                venueManagementService.createVenue(managerId, companyId, "Main Venue")
        );

        verify(venueRepository, never()).save(any());
    }

    // REQ: PRD-15, TST-13, UC20, UAT-61
    @Test
    void managerWithVenueConfigurationCanAddStandingZoneToVenue() {
        grantOnly(Permission.VENUE_CONFIGURATION);

        Venue venue = new Venue(VenueId.generate(), companyId, "Main Venue");
        when(venueRepository.findById(venue.getVenueId()))
                .thenReturn(Optional.of(venue));

        assertDoesNotThrow(() ->
                venueManagementService.addStandingZone(
                        managerId,
                        venue.getVenueId(),
                        "Standing",
                        BigDecimal.valueOf(100),
                        "ILS",
                        100
                )
        );

        verify(venueRepository).save(any());
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
        when(venueRepository.findById(venue.getVenueId()))
                .thenReturn(Optional.of(venue));

        assertThrows(SecurityException.class, () ->
                venueManagementService.addStandingZone(
                        managerId,
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
        lenient().when(permissionChecker.canManageEvents(managerId, companyId))
                .thenReturn(permission == Permission.EVENT_INVENTORY_MANAGEMENT);

        lenient().when(permissionChecker.canConfigureVenue(managerId, companyId))
                .thenReturn(permission == Permission.VENUE_CONFIGURATION);

        lenient().when(permissionChecker.canManagePurchasePolicies(managerId, companyId))
                .thenReturn(permission == Permission.MODIFY_POLICIES);

        lenient().when(permissionChecker.canManageDiscountPolicies(managerId, companyId))
                .thenReturn(permission == Permission.MODIFY_POLICIES);

        lenient().when(permissionChecker.canViewPurchaseHistory(managerId, companyId))
                .thenReturn(permission == Permission.VIEW_PURCHASE_HISTORY);

        lenient().when(permissionChecker.canGenerateSalesReport(managerId, companyId))
                .thenReturn(permission == Permission.GENERATE_SALES_REPORT);
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