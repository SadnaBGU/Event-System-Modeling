package com.eventsystem.infrastructure.api.policy;

import com.eventsystem.application.policy.PolicyManagementService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.EventStatus;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.policy.rule.IPolicy;
import com.eventsystem.domain.policy.rule.basic.AfterDatePolicy;
import com.eventsystem.domain.policy.rule.basic.CodePolicy;
import com.eventsystem.domain.policy.rule.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.rule.basic.MinAgePolicy;
import com.eventsystem.domain.policy.rule.basic.MinTicketPolicy;
import com.eventsystem.domain.policy.rule.basic.UntilDatePolicy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CompanyPolicyControllerTest {

    private static final MemberId ACTOR_ID = new MemberId("actor-123");
    private static final EventId EVENT_ID = new EventId("event-123");
    private static final CompanyId COMPANY_ID = new CompanyId("comp-123");

    private MockMvc mockMvc;

    @Mock
    private PolicyManagementService policyManagementService;

    @Mock private IEventRepository eventRepository;

    @InjectMocks private CompanyPolicyController companyPolicyController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(companyPolicyController)
                .setControllerAdvice(new com.eventsystem.infrastructure.api.exceptions.GlobalExceptionHandler())
                .build();

        // Event policy edits are only allowed while the event is a draft; provide a draft
        // event for the event-scoped tests. Lenient because company-scoped tests don't use it.
        Event draftEvent = mock(Event.class);
        lenient().when(draftEvent.status()).thenReturn(EventStatus.DRAFT);
        lenient().when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(draftEvent));
    }

    // =========================================================
    // Ownership routing tests
    // =========================================================

    @Test
    void setEventPolicy_WithMinAge_createsEventOwnedPolicy() throws Exception {
        String jsonBody = """
            {
                "type": "MIN_AGE",
                "value": 18
            }
            """;

        mockMvc.perform(put("/api/events/event-123/policies")
                        .requestAttr("authenticatedMemberId", ACTOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk());

        ArgumentCaptor<IPolicy> policyCaptor = ArgumentCaptor.forClass(IPolicy.class);

        verify(policyManagementService).createNewEventOwnedPurchasePolicy(
                eq(ACTOR_ID),
                eq(EVENT_ID),
                eq("API policy"),
                policyCaptor.capture());

        assertThat(policyCaptor.getValue()).isInstanceOf(MinAgePolicy.class);

        verify(policyManagementService, never()).createNewCompanyWidePurchasePolicy(
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void setCompanyPolicy_WithAndOperator_createsCompanyWidePolicy() throws Exception {
        String jsonBody = """
            {
                "type": "AND",
                "operands": [
                    { "type": "MIN_AGE", "value": 18 },
                    { "type": "MAX_TICKETS_PER_USER", "value": 4 }
                ]
            }
            """;

        mockMvc.perform(put("/api/companies/comp-123/policies")
                        .requestAttr("authenticatedMemberId", ACTOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk());

        verify(policyManagementService).createNewCompanyWidePurchasePolicy(
                eq(ACTOR_ID),
                eq(COMPANY_ID),
                eq("API policy"),
                any(IPolicy.class));

        verify(policyManagementService, never()).createNewEventOwnedPurchasePolicy(
                any(),
                any(),
                any(),
                any());
    }

    // =========================================================
    // Supported simple policy types
    // =========================================================

    @Test
    void setEventPolicy_WithCodePolicy_Returns200Ok() throws Exception {
        String jsonBody = """
            {
                "type": "CODE",
                "value": "VIP2026"
            }
            """;

        mockMvc.perform(put("/api/events/event-123/policies")
                        .requestAttr("authenticatedMemberId", ACTOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk());

        ArgumentCaptor<IPolicy> captor = ArgumentCaptor.forClass(IPolicy.class);

        verify(policyManagementService).createNewEventOwnedPurchasePolicy(
                eq(ACTOR_ID),
                eq(EVENT_ID),
                eq("API policy"),
                captor.capture());

        assertThat(captor.getValue()).isInstanceOf(CodePolicy.class);
    }

    @Test
    void setEventPolicy_WithBeforeDatePolicy_Returns200Ok() throws Exception {
        String jsonBody = """
            {
                "type": "BEFORE_DATE",
                "value": "2026-12-31"
            }
            """;

        mockMvc.perform(put("/api/events/event-123/policies")
                        .requestAttr("authenticatedMemberId", ACTOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk());

        ArgumentCaptor<IPolicy> captor = ArgumentCaptor.forClass(IPolicy.class);

        verify(policyManagementService).createNewEventOwnedPurchasePolicy(
                eq(ACTOR_ID),
                eq(EVENT_ID),
                eq("API policy"),
                captor.capture());

        assertThat(captor.getValue()).isInstanceOf(UntilDatePolicy.class);
    }

    @Test
    void setEventPolicy_WithAfterDatePolicy_Returns200Ok() throws Exception {
        String jsonBody = """
            {
                "type": "AFTER_DATE",
                "value": "2026-01-01"
            }
            """;

        mockMvc.perform(put("/api/events/event-123/policies")
                        .requestAttr("authenticatedMemberId", ACTOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk());

        ArgumentCaptor<IPolicy> captor = ArgumentCaptor.forClass(IPolicy.class);

        verify(policyManagementService).createNewEventOwnedPurchasePolicy(
                eq(ACTOR_ID),
                eq(EVENT_ID),
                eq("API policy"),
                captor.capture());

        assertThat(captor.getValue()).isInstanceOf(AfterDatePolicy.class);
    }

    @Test
    void setEventPolicy_WithMaxTicketsPolicy_Returns200Ok() throws Exception {
        String jsonBody = """
            {
                "type": "MAX_TICKETS_PER_USER",
                "value": 5
            }
            """;

        mockMvc.perform(put("/api/events/event-123/policies")
                        .requestAttr("authenticatedMemberId", ACTOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk());

        ArgumentCaptor<IPolicy> captor = ArgumentCaptor.forClass(IPolicy.class);

        verify(policyManagementService).createNewEventOwnedPurchasePolicy(
                eq(ACTOR_ID),
                eq(EVENT_ID),
                eq("API policy"),
                captor.capture());

        assertThat(captor.getValue()).isInstanceOf(MaxTicketPolicy.class);
    }

    @Test
    void setEventPolicy_WithMinTicketsPolicy_Returns200Ok() throws Exception {
        String jsonBody = """
            {
                "type": "MIN_TICKETS",
                "value": 2
            }
            """;

        mockMvc.perform(put("/api/events/event-123/policies")
                        .requestAttr("authenticatedMemberId", ACTOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk());

        ArgumentCaptor<IPolicy> captor = ArgumentCaptor.forClass(IPolicy.class);

        verify(policyManagementService).createNewEventOwnedPurchasePolicy(
                eq(ACTOR_ID),
                eq(EVENT_ID),
                eq("API policy"),
                captor.capture());

        assertThat(captor.getValue()).isInstanceOf(MinTicketPolicy.class);
    }

    // =========================================================
    // Composite policies
    // =========================================================

    @Test
    void setEventPolicy_WithOrOperator_Returns200Ok() throws Exception {
        String jsonBody = """
            {
                "type": "OR",
                "operands": [
                    { "type": "MIN_AGE", "value": 18 },
                    { "type": "CODE", "value": "SECRET" }
                ]
            }
            """;

        mockMvc.perform(put("/api/events/event-123/policies")
                        .requestAttr("authenticatedMemberId", ACTOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk());

        verify(policyManagementService).createNewEventOwnedPurchasePolicy(
                eq(ACTOR_ID),
                eq(EVENT_ID),
                eq("API policy"),
                any(IPolicy.class));
    }

    // =========================================================
    // Defensive programming / invalid input
    // =========================================================

    @Test
    void setEventPolicy_MissingType_ReturnsBadRequestAndDoesNotCallService() throws Exception {
        String jsonBody = """
            {
                "value": 18
            }
            """;

        mockMvc.perform(put("/api/events/event-123/policies")
                        .requestAttr("authenticatedMemberId", ACTOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest());

        verify(policyManagementService, never()).createNewEventOwnedPurchasePolicy(
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void setEventPolicy_UnsupportedType_ReturnsBadRequestAndDoesNotCallService() throws Exception {
        String jsonBody = """
            {
                "type": "UNKNOWN_POLICY",
                "value": 18
            }
            """;

        mockMvc.perform(put("/api/events/event-123/policies")
                        .requestAttr("authenticatedMemberId", ACTOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest());

        verify(policyManagementService, never()).createNewEventOwnedPurchasePolicy(
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void setEventPolicy_AndOperatorWithEmptyOperands_ReturnsBadRequestAndDoesNotCallService() throws Exception {
        String jsonBody = """
            {
                "type": "AND",
                "operands": []
            }
            """;

        mockMvc.perform(put("/api/events/event-123/policies")
                        .requestAttr("authenticatedMemberId", ACTOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest());

        verify(policyManagementService, never()).createNewEventOwnedPurchasePolicy(
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void setEventPolicy_InvalidInt_ReturnsBadRequestAndDoesNotCallService() throws Exception {
        String jsonBody = """
            {
                "type": "MIN_AGE",
                "value": "not-a-number"
            }
            """;

        mockMvc.perform(put("/api/events/event-123/policies")
                        .requestAttr("authenticatedMemberId", ACTOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest());

        verify(policyManagementService, never()).createNewEventOwnedPurchasePolicy(
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void setEventPolicy_BlankText_ReturnsBadRequestAndDoesNotCallService() throws Exception {
        String jsonBody = """
            {
                "type": "CODE",
                "value": "   "
            }
            """;

        mockMvc.perform(put("/api/events/event-123/policies")
                        .requestAttr("authenticatedMemberId", ACTOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest());

        verify(policyManagementService, never()).createNewEventOwnedPurchasePolicy(
                any(),
                any(),
                any(),
                any());
    }
}