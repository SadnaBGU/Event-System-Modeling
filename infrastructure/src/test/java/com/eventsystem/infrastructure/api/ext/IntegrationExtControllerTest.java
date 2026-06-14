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
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.policy.purchase.IPurchasePolicyRepository;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class IntegrationExtControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock private IProductionCompanyRepository companyRepository;
    @Mock private ProductionCompanyService companyService;
    @Mock private EventService eventService;
    @Mock private IEventRepository eventRepository;
    @Mock private IZoneRepository zoneRepository;
    @Mock private IPurchasePolicyRepository purchasePolicyRepository;

    @InjectMocks private IntegrationExtController controller;

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

        verify(eventService).publish(eq(new MemberId("actor-1")), eq(new EventId("evt-1")));
    }

    @SuppressWarnings("null")
    @Test
    void addZone_valid_returnsOk() throws Exception {
        AddZoneRequest req = new AddZoneRequest("Front", 12.5, "USD", 100);
        doNothing().when(zoneRepository).save(any(Zone.class));

        mockMvc.perform(post("/api/events/event-1/zones")
                .requestAttr("authenticatedMemberId", new MemberId("actor-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @SuppressWarnings("null")
    @Test
    void addZone_missingCurrency_defaultsToUSD() throws Exception {
        AddZoneRequest req = new AddZoneRequest("Front", 12.5, "  ", 100);
        doNothing().when(zoneRepository).save(any(Zone.class));

        mockMvc.perform(post("/api/events/event-1/zones")
                .requestAttr("authenticatedMemberId", new MemberId("actor-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void addZone_invalidInputs_throwsExceptions() {
        MemberId actor = new MemberId("actor-1");
        String evt = "event-1";

        assertThrows(IllegalArgumentException.class, () ->
                controller.addZone(actor, evt, null));

        assertThrows(IllegalArgumentException.class, () ->
                controller.addZone(actor, evt, new AddZoneRequest(null, 10.0, "USD", 100)));

        assertThrows(IllegalArgumentException.class, () ->
                controller.addZone(actor, evt, new AddZoneRequest("  ", 10.0, "USD", 100)));

        assertThrows(IllegalArgumentException.class, () ->
                controller.addZone(actor, evt, new AddZoneRequest("Zone", 10.0, "USD", 0)));

        assertThrows(IllegalArgumentException.class, () ->
                controller.addZone(actor, evt, new AddZoneRequest("Zone", null, "USD", 100)));
    }

    // =========================================================
    // Companies & Roles
    // =========================================================

    @SuppressWarnings("null")
    @Test
    void listCompanies_empty_returnsOk() throws Exception {
        when(companyRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void listCompanies_allStatuses_returnsOk() throws Exception {
        ProductionCompany c1 = mockCompany("c1", CompanyStatus.ACTIVE);
        ProductionCompany c2 = mockCompany("c2", CompanyStatus.SUSPENDED);
        ProductionCompany c3 = mockCompany("c3", CompanyStatus.TERMINATED);
        ProductionCompany c4 = mockCompany("c4", CompanyStatus.ADMIN_CLOSED);

        when(companyRepository.findAll()).thenReturn(List.of(c1, c2, c3, c4));

        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].status").value("SUSPENDED"))
                .andExpect(jsonPath("$[2].status").value("CLOSED"))
                .andExpect(jsonPath("$[3].status").value("CLOSED"));
    }

    @Test
    void getCompany_notFound_returnsBadRequest() throws Exception {
        when(companyRepository.findById(any(CompanyId.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/companies/comp-999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCompany_found_returnsOk() throws Exception {
        ProductionCompany c1 = mockCompany("c1", CompanyStatus.ACTIVE);
        when(companyRepository.findById(any(CompanyId.class))).thenReturn(Optional.of(c1));

        mockMvc.perform(get("/api/companies/comp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value("c1"));
    }

    @Test
    void listRoles_reflectionFails_returnsEmptyList() throws Exception {
        ProductionCompany c = mockCompany("c1", CompanyStatus.ACTIVE);

        Class<?> treeClass = Class.forName("com.eventsystem.domain.company.AppointmentTree");

        Object badTree = mock(treeClass, invocation -> {
            if (invocation.getMethod().getName().equals("root")) {
                throw new RuntimeException("Force reflection failure");
            }
            return null;
        });

        Field f = ProductionCompany.class.getDeclaredField("appointmentTree");
        f.setAccessible(true);
        f.set(c, badTree);

        when(companyRepository.findById(any(CompanyId.class))).thenReturn(Optional.of(c));

        mockMvc.perform(get("/api/companies/comp-1/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listRoles_treePopulated_returnsHierarchy() throws Exception {
        ProductionCompany c = mockCompany("c1", CompanyStatus.ACTIVE);

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

        Field f = ProductionCompany.class.getDeclaredField("appointmentTree");
        f.setAccessible(true);
        f.set(c, goodTree);

        when(companyRepository.findById(any(CompanyId.class))).thenReturn(Optional.of(c));

        mockMvc.perform(get("/api/companies/comp-1/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").value("owner-1"))
                .andExpect(jsonPath("$[0].roleType").value("OWNER"))
                .andExpect(jsonPath("$[1].memberId").value("mgr-1"))
                .andExpect(jsonPath("$[1].roleType").value("MANAGER"))
                .andExpect(jsonPath("$[2].memberId").value("mgr-2"))
                .andExpect(jsonPath("$[3].memberId").value("owner-2"));
    }

    // =========================================================
    // Policies
    // =========================================================

    @Test
    void getCompanyPolicies_returnsMappedPolicies() throws Exception {
        PurchasePolicy p1 = mock(PurchasePolicy.class, Answers.RETURNS_DEEP_STUBS);
        when(p1.id().value()).thenReturn("p1");
        when(p1.policyName()).thenReturn("Pol1");
        when(p1.scope()).thenReturn(null);
        when(p1.policy()).thenReturn(null);

        PurchasePolicy p2 = mock(PurchasePolicy.class, Answers.RETURNS_DEEP_STUBS);
        when(p2.id().value()).thenReturn("p2");
        when(p2.policyName()).thenReturn("Pol2");
        when(p2.scope()).thenReturn(mock(PolicyScope.class));
        when(p2.policy()).thenReturn(mock(IPolicy.class));

        when(purchasePolicyRepository.findActiveByCompanyId(any())).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/api/companies/comp-1/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].scope").isEmpty())
                .andExpect(jsonPath("$.items[1].scope").isNotEmpty());
    }

    @Test
    void getEventPolicies_returnsMappedPolicies() throws Exception {
        when(purchasePolicyRepository.findApplicableToEvent(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/events/evt-1/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    // =========================================================
    // Helpers
    // =========================================================

    private ProductionCompany mockCompany(String id, CompanyStatus status) {
        ProductionCompany c = mock(ProductionCompany.class, Answers.RETURNS_DEEP_STUBS);
        lenient().when(c.companyId().value()).thenReturn(id);
        lenient().when(c.companyDetails().name()).thenReturn("Name-" + id);
        lenient().when(c.companyDetails().description()).thenReturn("Desc-" + id);
        lenient().when(c.status()).thenReturn(status);
        return c;
    }
}