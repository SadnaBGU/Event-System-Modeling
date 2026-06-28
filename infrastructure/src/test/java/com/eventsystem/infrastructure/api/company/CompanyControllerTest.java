package com.eventsystem.infrastructure.api.company;

import com.eventsystem.application.company.ProductionCompanyService;
import com.eventsystem.application.event.EventService;
import com.eventsystem.application.order.ReportService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CompanyControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock private ProductionCompanyService productionCompanyService;
    @Mock private EventService eventService;
    @Mock private ReportService reportService;

    @InjectMocks private CompanyController companyController;

    @BeforeEach
    void setUp() {
        
        mockMvc = MockMvcBuilders.standaloneSetup(companyController).build();
        
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        mockMvc = MockMvcBuilders.standaloneSetup(companyController)
                
                .setControllerAdvice(new com.eventsystem.infrastructure.api.exceptions.GlobalExceptionHandler())
                .build();
        
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    
    }

    @SuppressWarnings("null")
@Test
    void createCompany_Returns201Created() throws Exception {
        CompanyController.CreateCompanyRequest request = 
                new CompanyController.CreateCompanyRequest("Test Co", "test@co.com");
        
        when(productionCompanyService.createCompany(any(), anyString(), anyString(), anyDouble()))
                .thenReturn(new CompanyId("comp-999"));

        mockMvc.perform(post("/api/companies")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/companies/comp-999"));
    }

    @SuppressWarnings("null")
@Test
    void updateCompanyStatus_Returns204NoContent() throws Exception {
        CompanyController.CompanyStatusRequest request = 
                new CompanyController.CompanyStatusRequest("SUSPENDED");

        mockMvc.perform(patch("/api/companies/comp-123/status")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @SuppressWarnings("null")
@Test
    void createEvent_Returns201Created() throws Exception {
        CompanyController.CreateEventRequest request = new CompanyController.CreateEventRequest(
                "My Event", List.of(LocalDateTime.now().plusDays(1)), "Music", "Tel Aviv", "A great show");

        when(eventService.createDraft(any(), any(), any()))
                .thenReturn(new EventId("event-555"));

        mockMvc.perform(post("/api/companies/comp-123/events")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/events/event-555"));
    }
    // --- טסטים לדוח מכירות ---
    @Test
    void getSalesReport_Returns200Ok() throws Exception {
        when(productionCompanyService.hasPermission(any(), any(), any())).thenReturn(true);
        when(reportService.generateCompanySalesReport(any(), any())).thenReturn(
                new ReportService.CompanySalesReportDTO("comp-123", java.time.Instant.now(), java.math.BigDecimal.ZERO, List.of())
        );

        mockMvc.perform(get("/api/companies/comp-123/reports/sales")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123")))
                .andExpect(status().isOk());
    }

    @Test
    void getSalesReport_ThrowsSecurityException_WhenNoPermission() throws Exception {
        when(productionCompanyService.hasPermission(any(), any(), any())).thenReturn(false);

        mockMvc.perform(get("/api/companies/comp-123/reports/sales")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123")))
                .andExpect(status().isForbidden()); // מצפה ל-403 Forbidden
    }

    // --- טסטים לעץ המינויים (Roles) ---
    @SuppressWarnings("null")
@Test
    void appointRole_Owner_Returns200Ok() throws Exception {
        CompanyController.RoleRequest request = new CompanyController.RoleRequest("target-1", "OWNER", null);
        when(productionCompanyService.resolveMemberByUsername("target-1"))
                .thenReturn(new MemberId("target-1"));

        mockMvc.perform(post("/api/companies/comp-123/roles")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(productionCompanyService).appointOwner(
                eq(new CompanyId("comp-123")), eq(new MemberId("actor-123")), eq(new MemberId("target-1")));
    }

    @SuppressWarnings("null")
@Test
    void appointRole_Manager_Returns200Ok() throws Exception {
        CompanyController.RoleRequest request = new CompanyController.RoleRequest(
                "target-1", "MANAGER", List.of("GENERATE_SALES_REPORT"));
        when(productionCompanyService.resolveMemberByUsername("target-1"))
                .thenReturn(new MemberId("target-1"));

        mockMvc.perform(post("/api/companies/comp-123/roles")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(productionCompanyService).appointManager(
                eq(new CompanyId("comp-123")), eq(new MemberId("actor-123")),
                eq(new MemberId("target-1")), any());
    }

    @SuppressWarnings("null")
@Test
    void appointRole_Manager_EmptyPermissions_ThrowsException() throws Exception {
        CompanyController.RoleRequest request = new CompanyController.RoleRequest("target-1", "MANAGER", List.of());

        mockMvc.perform(post("/api/companies/comp-123/roles")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()); // מצפה ל-400 Bad Request
    }

    @Test
    void acceptRoleAppointment_Returns200Ok() throws Exception {
        mockMvc.perform(post("/api/companies/comp-123/roles/actor-123/accept")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123")))
                .andExpect(status().isOk());
    }

    @Test
    void removeAppointee_Returns204NoContent() throws Exception {
        mockMvc.perform(delete("/api/companies/comp-123/roles/target-1")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123")))
                .andExpect(status().isNoContent());
    }
    
}