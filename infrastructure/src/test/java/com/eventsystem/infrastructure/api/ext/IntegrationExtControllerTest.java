package com.eventsystem.infrastructure.api.ext;

import com.eventsystem.application.company.ProductionCompanyService;
import com.eventsystem.application.event.EventService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.IProductionCompanyRepository;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.policy.purchase.IPurchasePolicyRepository;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.infrastructure.api.exceptions.GlobalExceptionHandler;
import com.eventsystem.infrastructure.api.ext.IntegrationExtController.AddZoneRequest;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
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

    @SuppressWarnings("null")
    @Test
    void listCompanies_empty_returnsOk() throws Exception {
        when(companyRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void getCompany_notFound_returnsBadRequest() throws Exception {
        when(companyRepository.findById(any(CompanyId.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/companies/comp-999"))
                .andExpect(status().isBadRequest());
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
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @SuppressWarnings("null")
    @Test
    void addZone_invalid_missingName_returnsBadRequest() throws Exception {
        AddZoneRequest req = new AddZoneRequest("", 10.0, "USD", 10);

        mockMvc.perform(post("/api/events/event-1/zones")
                .requestAttr("authenticatedMemberId", new MemberId("actor-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
