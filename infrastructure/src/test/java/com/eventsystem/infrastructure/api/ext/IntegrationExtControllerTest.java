package com.eventsystem.infrastructure.api.ext;

import com.eventsystem.application.company.ProductionCompanyService;
import com.eventsystem.application.event.EventService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.CompanyStatus;
import com.eventsystem.domain.company.IProductionCompanyRepository;
import com.eventsystem.domain.company.ManagerNode;
import com.eventsystem.domain.company.OwnerNode;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.domain.policy.discount.IDiscountPolicyRepository;
import com.eventsystem.domain.policy.purchase.IPurchasePolicyRepository;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.domain.policy.rule.IPolicy;
import com.eventsystem.domain.policy.shared.PolicyScope;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.infrastructure.api.exceptions.GlobalExceptionHandler;
import com.eventsystem.infrastructure.api.ext.IntegrationExtController.AddZoneRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class IntegrationExtControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private IProductionCompanyRepository companyRepository;

    @Mock
    private ProductionCompanyService companyService;

    @Mock
    private EventService eventService;

    @Mock
    private IEventRepository eventRepository;

    @Mock
    private IZoneRepository zoneRepository;

    @Mock
    private IPurchasePolicyRepository purchasePolicyRepository;

    @Mock
    private IDiscountPolicyRepository discountPolicyRepository;

    @Mock
    private IMemberRepository memberRepository;

    @InjectMocks
    private IntegrationExtController controller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // =========================================================
    // Events
    // =========================================================

    @Test
    void publishEvent_valid_returnsNoContent() throws Exception {
        mockMvc.perform(post("/api/events/evt-1/publish")
                        .requestAttr("authenticatedMemberId", new MemberId("actor-1")))
                .andExpect(status().isNoContent());

        verify(eventService).publish(
                eq(new MemberId("actor-1")),
                eq(new EventId("evt-1")));
    }

    @Test
    void addZone_valid_returnsOk() throws Exception {
        AddZoneRequest request = new AddZoneRequest(
                "Front",
                BigDecimal.valueOf(12.5),
                "USD",
                100);

        doNothing().when(zoneRepository).save(any(Zone.class));

        mockMvc.perform(post("/api/events/event-1/zones")
                        .requestAttr("authenticatedMemberId", new MemberId("actor-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneId").exists())
                .andExpect(jsonPath("$.zoneName").value("Front"))
                .andExpect(jsonPath("$.price").value(12.5))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.totalCapacity").value(100))
                .andExpect(jsonPath("$.availableCount").value(100));

        verify(zoneRepository).save(any(Zone.class));
        verify(eventService).addZone(
                eq(new MemberId("actor-1")),
                eq(new EventId("event-1")),
                any());
    }

    @Test
    void addZone_missingCurrency_defaultsToUSD() throws Exception {
        AddZoneRequest request = new AddZoneRequest(
                "Front",
                BigDecimal.valueOf(12.5),
                "  ",
                100);

        doNothing().when(zoneRepository).save(any(Zone.class));

        mockMvc.perform(post("/api/events/event-1/zones")
                        .requestAttr("authenticatedMemberId", new MemberId("actor-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void addZone_invalidInputs_throwsExceptions() {
        MemberId actor = new MemberId("actor-1");
        String eventId = "event-1";

        assertThrows(IllegalArgumentException.class,
                () -> controller.addZone(actor, eventId, null));

        assertThrows(IllegalArgumentException.class,
                () -> controller.addZone(
                        actor,
                        eventId,
                        new AddZoneRequest(null, BigDecimal.TEN, "USD", 100)));

        assertThrows(IllegalArgumentException.class,
                () -> controller.addZone(
                        actor,
                        eventId,
                        new AddZoneRequest("  ", BigDecimal.TEN, "USD", 100)));

        assertThrows(IllegalArgumentException.class,
                () -> controller.addZone(
                        actor,
                        eventId,
                        new AddZoneRequest("Zone", BigDecimal.TEN, "USD", 0)));

        assertThrows(IllegalArgumentException.class,
                () -> controller.addZone(
                        actor,
                        eventId,
                        new AddZoneRequest("Zone", null, "USD", 100)));

        assertThrows(IllegalArgumentException.class,
                () -> controller.addZone(
                        actor,
                        eventId,
                        new AddZoneRequest("Zone", BigDecimal.valueOf(-1), "USD", 100)));
    }

    // =========================================================
    // Companies & Roles
    // =========================================================

    @Test
    void listCompanies_empty_returnsOk() throws Exception {
        when(companyRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/companies")
                        .requestAttr("authenticatedMemberId", new MemberId("actor-1")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listCompanies_onlyReturnsCompaniesTheMemberCanActOn() throws Exception {
        MemberId actor = new MemberId("actor-1");

        ProductionCompany c1 = mockCompany("c1", CompanyStatus.ACTIVE);
        lenient().when(c1.isOwner(actor)).thenReturn(true);

        ProductionCompany c2 = mockCompany("c2", CompanyStatus.SUSPENDED);
        lenient().when(c2.isManager(actor)).thenReturn(true);

        ProductionCompany c3 = mockCompany("c3", CompanyStatus.TERMINATED);
        ProductionCompany c4 = mockCompany("c4", CompanyStatus.ADMIN_CLOSED);

        when(companyRepository.findAll()).thenReturn(List.of(c1, c2, c3, c4));

        mockMvc.perform(get("/api/companies")
                        .requestAttr("authenticatedMemberId", actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].companyId").value("c1"))
                .andExpect(jsonPath("$[0].companyName").value("Name-c1"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].companyId").value("c2"))
                .andExpect(jsonPath("$[1].status").value("SUSPENDED"));
    }

    @Test
    void getCompany_notFound_returnsBadRequest() throws Exception {
        when(companyRepository.findById(any(CompanyId.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/companies/comp-999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCompany_found_returnsOk() throws Exception {
        ProductionCompany company = mockCompany("c1", CompanyStatus.ACTIVE);
        when(companyRepository.findById(any(CompanyId.class))).thenReturn(Optional.of(company));

        mockMvc.perform(get("/api/companies/comp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value("c1"))
                .andExpect(jsonPath("$.companyName").value("Name-c1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.contactDetails").value("Desc-c1"));
    }

    @Test
    void listRoles_companyNotFound_returnsBadRequest() throws Exception {
        when(companyRepository.findById(any(CompanyId.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/companies/comp-404/roles"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listRoles_reflectionFails_returnsEmptyList() throws Exception {
        ProductionCompany company = mockCompany("c1", CompanyStatus.ACTIVE);

        Class<?> treeClass = Class.forName("com.eventsystem.domain.company.AppointmentTree");

        Object badTree = mock(treeClass, invocation -> {
            if (invocation.getMethod().getName().equals("root")) {
                throw new RuntimeException("Force reflection failure");
            }
            return null;
        });

        Field field = ProductionCompany.class.getDeclaredField("appointmentTree");
        field.setAccessible(true);
        field.set(company, badTree);

        when(companyRepository.findById(any(CompanyId.class))).thenReturn(Optional.of(company));

        mockMvc.perform(get("/api/companies/comp-1/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listRoles_treePopulated_returnsHierarchy() throws Exception {
        ProductionCompany company = mockCompany("c1", CompanyStatus.ACTIVE);

        OwnerNode owner = mock(OwnerNode.class);
        when(owner.memberId()).thenReturn(new MemberId("owner-1"));

        ManagerNode mgr1 = mock(ManagerNode.class);
        when(mgr1.memberId()).thenReturn(new MemberId("mgr-1"));
        when(mgr1.permissions()).thenReturn(Set.of());

        ManagerNode mgr2 = mock(ManagerNode.class);
        when(mgr2.memberId()).thenReturn(new MemberId("mgr-2"));
        when(mgr2.permissions()).thenReturn(Set.of());
        when(mgr2.appointedManagers()).thenReturn(List.of());

        when(mgr1.appointedManagers()).thenReturn(List.of(mgr2));
        when(owner.appointedManagers()).thenReturn(List.of(mgr1));

        OwnerNode owner2 = mock(OwnerNode.class);
        when(owner2.memberId()).thenReturn(new MemberId("owner-2"));
        when(owner2.appointedManagers()).thenReturn(List.of());
        when(owner2.appointedOwners()).thenReturn(List.of());

        when(owner.appointedOwners()).thenReturn(List.of(owner2));

        Class<?> treeClass = Class.forName("com.eventsystem.domain.company.AppointmentTree");

        Object goodTree = mock(treeClass, invocation -> {
            if (invocation.getMethod().getName().equals("root")) {
                return owner;
            }
            return null;
        });

        Field field = ProductionCompany.class.getDeclaredField("appointmentTree");
        field.setAccessible(true);
        field.set(company, goodTree);

        when(companyRepository.findById(any(CompanyId.class))).thenReturn(Optional.of(company));

        mockMvc.perform(get("/api/companies/comp-1/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").value("owner-1"))
                .andExpect(jsonPath("$[0].roleType").value("OWNER"))
                .andExpect(jsonPath("$[1].memberId").value("mgr-1"))
                .andExpect(jsonPath("$[1].roleType").value("MANAGER"))
                .andExpect(jsonPath("$[2].memberId").value("mgr-2"))
                .andExpect(jsonPath("$[2].roleType").value("MANAGER"))
                .andExpect(jsonPath("$[3].memberId").value("owner-2"))
                .andExpect(jsonPath("$[3].roleType").value("OWNER"));
    }

    // =========================================================
    // Policies
    // =========================================================

    @Test
    void getCompanyPolicies_returnsCompanyOwnedPurchasePoliciesInPurchasePoliciesArray() throws Exception {
        CompanyId companyId = new CompanyId("comp-1");

        PurchasePolicy p1 = mockCompanyOwnedPurchasePolicy(
                "p1",
                "Company wide policy",
                companyId,
                PolicyScope.companyWideScope(),
                true);

        PurchasePolicy p2 = mockCompanyOwnedPurchasePolicy(
                "p2",
                "Company event-scoped policy",
                companyId,
                PolicyScope.forSingleEvent(new EventId("event-1")),
                true);

        when(purchasePolicyRepository.findCompanyOwnedPolicies(companyId))
                .thenReturn(List.of(p1, p2));

        when(discountPolicyRepository.findCompanyOwnedPolicies(companyId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/companies/comp-1/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasePolicies.length()").value(2))
                .andExpect(jsonPath("$.purchasePolicies[0].policyId").value("p1"))
                .andExpect(jsonPath("$.purchasePolicies[0].policyName").value("Company wide policy"))
                .andExpect(jsonPath("$.purchasePolicies[0].policyType").value("PURCHASE"))
                .andExpect(jsonPath("$.purchasePolicies[0].companyId").value("comp-1"))
                .andExpect(jsonPath("$.purchasePolicies[0].active").value(true))
                .andExpect(jsonPath("$.purchasePolicies[0].ownerType").value("COMPANY"))
                .andExpect(jsonPath("$.purchasePolicies[0].scope.companyWide").value(true))
                .andExpect(jsonPath("$.purchasePolicies[0].scope.eventIds").isEmpty())
                .andExpect(jsonPath("$.purchasePolicies[1].policyId").value("p2"))
                .andExpect(jsonPath("$.purchasePolicies[1].policyType").value("PURCHASE"))
                .andExpect(jsonPath("$.purchasePolicies[1].ownerType").value("COMPANY"))
                .andExpect(jsonPath("$.purchasePolicies[1].scope.companyWide").value(false))
                .andExpect(jsonPath("$.purchasePolicies[1].scope.eventIds[0]").value("event-1"))
                .andExpect(jsonPath("$.discountPolicies").isEmpty());

        verify(purchasePolicyRepository).findCompanyOwnedPolicies(companyId);
        verify(discountPolicyRepository).findCompanyOwnedPolicies(companyId);
        verify(purchasePolicyRepository, never()).findActiveByCompanyId(any());
    }

    @Test
    void getCompanyPolicies_returnsCompanyOwnedDiscountPoliciesInDiscountPoliciesArray() throws Exception {
        CompanyId companyId = new CompanyId("comp-1");

        DiscountPolicy discountPolicy = mockCompanyOwnedDiscountPolicy(
                "d1",
                companyId,
                PolicyScope.companyWideScope(),
                true);

        when(purchasePolicyRepository.findCompanyOwnedPolicies(companyId))
                .thenReturn(List.of());

        when(discountPolicyRepository.findCompanyOwnedPolicies(companyId))
                .thenReturn(List.of(discountPolicy));

        mockMvc.perform(get("/api/companies/comp-1/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasePolicies").isEmpty())
                .andExpect(jsonPath("$.discountPolicies.length()").value(1))
                .andExpect(jsonPath("$.discountPolicies[0].policyId").value("d1"))
                .andExpect(jsonPath("$.discountPolicies[0].policyName").value("Discount Policy"))
                .andExpect(jsonPath("$.discountPolicies[0].policyType").value("DISCOUNT"))
                .andExpect(jsonPath("$.discountPolicies[0].companyId").value("comp-1"))
                .andExpect(jsonPath("$.discountPolicies[0].active").value(true))
                .andExpect(jsonPath("$.discountPolicies[0].ownerType").value("COMPANY"))
                .andExpect(jsonPath("$.discountPolicies[0].scope.companyWide").value(true))
                .andExpect(jsonPath("$.discountPolicies[0].scope.eventIds").isEmpty())
                .andExpect(jsonPath("$.discountPolicies[0].stackable").value(false))
                .andExpect(jsonPath("$.discountPolicies[0].discounts").isEmpty())
                .andExpect(jsonPath("$.discountPolicies[0].summary").value("0 discount(s)"));

        verify(purchasePolicyRepository).findCompanyOwnedPolicies(companyId);
        verify(discountPolicyRepository).findCompanyOwnedPolicies(companyId);
    }

    @Test
    void getCompanyPolicies_whenNoPolicies_returnsEmptySplitArrays() throws Exception {
        CompanyId companyId = new CompanyId("comp-1");

        when(purchasePolicyRepository.findCompanyOwnedPolicies(companyId))
                .thenReturn(List.of());

        when(discountPolicyRepository.findCompanyOwnedPolicies(companyId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/companies/comp-1/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasePolicies").isEmpty())
                .andExpect(jsonPath("$.discountPolicies").isEmpty());

        verify(purchasePolicyRepository).findCompanyOwnedPolicies(companyId);
        verify(discountPolicyRepository).findCompanyOwnedPolicies(companyId);
    }

    @Test
    void getEventPolicies_returnsApplicablePurchasePoliciesInPurchasePoliciesArray() throws Exception {
        EventId eventId = new EventId("evt-1");
        CompanyId companyId = new CompanyId("company-of-event");

        PurchasePolicy companyPolicy = mockCompanyOwnedPurchasePolicy(
                "company-policy",
                "Company policy",
                companyId,
                PolicyScope.companyWideScope(),
                true);

        PurchasePolicy eventPolicy = mockEventOwnedPurchasePolicy(
                "event-policy",
                "Event policy",
                companyId,
                PolicyScope.forSingleEvent(eventId),
                true);

        when(eventService.companyOfEvent(eventId)).thenReturn(companyId);

        when(purchasePolicyRepository.findApplicableToPurchase(companyId, eventId))
                .thenReturn(List.of(companyPolicy, eventPolicy));

        when(discountPolicyRepository.findApplicableToPurchase(companyId, eventId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/events/evt-1/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasePolicies.length()").value(2))
                .andExpect(jsonPath("$.purchasePolicies[0].policyId").value("company-policy"))
                .andExpect(jsonPath("$.purchasePolicies[0].policyType").value("PURCHASE"))
                .andExpect(jsonPath("$.purchasePolicies[0].ownerType").value("COMPANY"))
                .andExpect(jsonPath("$.purchasePolicies[0].scope.companyWide").value(true))
                .andExpect(jsonPath("$.purchasePolicies[1].policyId").value("event-policy"))
                .andExpect(jsonPath("$.purchasePolicies[1].policyType").value("PURCHASE"))
                .andExpect(jsonPath("$.purchasePolicies[1].ownerType").value("EVENT"))
                .andExpect(jsonPath("$.purchasePolicies[1].scope.companyWide").value(false))
                .andExpect(jsonPath("$.purchasePolicies[1].scope.eventIds[0]").value("evt-1"))
                .andExpect(jsonPath("$.discountPolicies").isEmpty());

        verify(eventService).companyOfEvent(eventId);
        verify(purchasePolicyRepository).findApplicableToPurchase(companyId, eventId);
        verify(discountPolicyRepository).findApplicableToPurchase(companyId, eventId);
    }

    @Test
    void getEventPolicies_returnsApplicableDiscountPoliciesInDiscountPoliciesArray() throws Exception {
        EventId eventId = new EventId("evt-1");
        CompanyId companyId = new CompanyId("company-of-event");

        DiscountPolicy discountPolicy = mockEventOwnedDiscountPolicy(
                "event-discount",
                companyId,
                PolicyScope.forSingleEvent(eventId),
                true);

        when(eventService.companyOfEvent(eventId)).thenReturn(companyId);

        when(purchasePolicyRepository.findApplicableToPurchase(companyId, eventId))
                .thenReturn(List.of());

        when(discountPolicyRepository.findApplicableToPurchase(companyId, eventId))
                .thenReturn(List.of(discountPolicy));

        mockMvc.perform(get("/api/events/evt-1/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasePolicies").isEmpty())
                .andExpect(jsonPath("$.discountPolicies.length()").value(1))
                .andExpect(jsonPath("$.discountPolicies[0].policyId").value("event-discount"))
                .andExpect(jsonPath("$.discountPolicies[0].policyType").value("DISCOUNT"))
                .andExpect(jsonPath("$.discountPolicies[0].companyId").value("company-of-event"))
                .andExpect(jsonPath("$.discountPolicies[0].active").value(true))
                .andExpect(jsonPath("$.discountPolicies[0].ownerType").value("EVENT"))
                .andExpect(jsonPath("$.discountPolicies[0].scope.companyWide").value(false))
                .andExpect(jsonPath("$.discountPolicies[0].scope.eventIds[0]").value("evt-1"));

        verify(eventService).companyOfEvent(eventId);
        verify(purchasePolicyRepository).findApplicableToPurchase(companyId, eventId);
        verify(discountPolicyRepository).findApplicableToPurchase(companyId, eventId);
    }

    @Test
    void getEventPolicies_whenNoApplicablePolicies_returnsEmptySplitArrays() throws Exception {
        EventId eventId = new EventId("evt-1");
        CompanyId companyId = new CompanyId("company-of-event");

        when(eventService.companyOfEvent(eventId)).thenReturn(companyId);

        when(purchasePolicyRepository.findApplicableToPurchase(companyId, eventId))
                .thenReturn(List.of());

        when(discountPolicyRepository.findApplicableToPurchase(companyId, eventId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/events/evt-1/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasePolicies").isEmpty())
                .andExpect(jsonPath("$.discountPolicies").isEmpty());

        verify(eventService).companyOfEvent(eventId);
        verify(purchasePolicyRepository).findApplicableToPurchase(companyId, eventId);
        verify(discountPolicyRepository).findApplicableToPurchase(companyId, eventId);
    }

    // =========================================================
    // Helpers
    // =========================================================

    private ProductionCompany mockCompany(String id, CompanyStatus status) {
        ProductionCompany company = mock(ProductionCompany.class, Answers.RETURNS_DEEP_STUBS);

        lenient().when(company.companyId().value()).thenReturn(id);
        lenient().when(company.companyDetails().name()).thenReturn("Name-" + id);
        lenient().when(company.companyDetails().description()).thenReturn("Desc-" + id);
        lenient().when(company.status()).thenReturn(status);

        return company;
    }

    private PurchasePolicy mockCompanyOwnedPurchasePolicy(
            String id,
            String name,
            CompanyId companyId,
            PolicyScope scope,
            boolean active) {
        PurchasePolicy policy = mock(PurchasePolicy.class);

        when(policy.id()).thenReturn(new PurchasePolicyId(id));
        when(policy.policyName()).thenReturn(name);
        when(policy.companyId()).thenReturn(companyId);
        when(policy.scope()).thenReturn(scope);
        when(policy.isActive()).thenReturn(active);
        when(policy.isEventPolicy()).thenReturn(false);
        when(policy.policy()).thenReturn(mock(IPolicy.class));

        return policy;
    }

    private PurchasePolicy mockEventOwnedPurchasePolicy(
            String id,
            String name,
            CompanyId companyId,
            PolicyScope scope,
            boolean active) {
        PurchasePolicy policy = mock(PurchasePolicy.class);

        when(policy.id()).thenReturn(new PurchasePolicyId(id));
        when(policy.policyName()).thenReturn(name);
        when(policy.companyId()).thenReturn(companyId);
        when(policy.scope()).thenReturn(scope);
        when(policy.isActive()).thenReturn(active);
        when(policy.isEventPolicy()).thenReturn(true);
        when(policy.policy()).thenReturn(mock(IPolicy.class));

        return policy;
    }

    private DiscountPolicy mockCompanyOwnedDiscountPolicy(
            String id,
            CompanyId companyId,
            PolicyScope scope,
            boolean active) {
        DiscountPolicy policy = mock(DiscountPolicy.class);

        when(policy.id()).thenReturn(new DiscountPolicyId(id));
        when(policy.companyId()).thenReturn(companyId);
        when(policy.scope()).thenReturn(scope);
        when(policy.isActive()).thenReturn(active);
        when(policy.isEventPolicy()).thenReturn(false);
        when(policy.isStackable()).thenReturn(false);
        when(policy.getDiscountInfos()).thenReturn(List.of());
        when(policy.discounts()).thenReturn(List.of());

        return policy;
    }

    private DiscountPolicy mockEventOwnedDiscountPolicy(
            String id,
            CompanyId companyId,
            PolicyScope scope,
            boolean active) {
        DiscountPolicy policy = mock(DiscountPolicy.class);

        when(policy.id()).thenReturn(new DiscountPolicyId(id));
        when(policy.companyId()).thenReturn(companyId);
        when(policy.scope()).thenReturn(scope);
        when(policy.isActive()).thenReturn(active);
        when(policy.isEventPolicy()).thenReturn(true);
        when(policy.isStackable()).thenReturn(false);
        when(policy.getDiscountInfos()).thenReturn(List.of());
        when(policy.discounts()).thenReturn(List.of());

        return policy;
    }
}