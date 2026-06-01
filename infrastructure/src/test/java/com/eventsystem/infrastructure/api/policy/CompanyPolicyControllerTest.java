package com.eventsystem.infrastructure.api.policy;

import com.eventsystem.application.policy.PurchasePolicyService;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CompanyPolicyControllerTest {

    private MockMvc mockMvc;

    @Mock private PurchasePolicyService purchasePolicyService;

    @InjectMocks private CompanyPolicyController companyPolicyController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(companyPolicyController)
                .setControllerAdvice(new com.eventsystem.infrastructure.api.exceptions.GlobalExceptionHandler())
                .build();
    }

    @Test
    void setEventPolicy_WithMinAge_Returns200Ok() throws Exception {
        String jsonBody = """
            {
                "type": "MIN_AGE",
                "value": 18
            }
            """;

        mockMvc.perform(put("/api/events/event-123/policies")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk());
    }

    @Test
    void setCompanyPolicy_WithAndOperator_Returns200Ok() throws Exception {
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
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk());
    }
    // --- טסטים לסוגי מדיניות נוספים ---
    @Test
    void setEventPolicy_WithCodePolicy_Returns200Ok() throws Exception {
        String jsonBody = """
            {
                "type": "CODE",
                "value": "VIP2026"
            }
            """;
        mockMvc.perform(put("/api/events/event-123/policies")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk());
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
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk());
    }

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
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk());
    }

    // --- טסטים לשגיאות (Defensive Programming) ---
    @Test
    void setEventPolicy_MissingType_ThrowsException() throws Exception {
        String jsonBody = """
            {
                "value": 18
            }
            """;
        mockMvc.perform(put("/api/events/event-123/policies")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isBadRequest()); // מצפה ל-400
    }

    @Test
    void setEventPolicy_AndOperatorWithEmptyOperands_ThrowsException() throws Exception {
        String jsonBody = """
            {
                "type": "AND",
                "operands": [] 
            }
            """;
        mockMvc.perform(put("/api/events/event-123/policies")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isBadRequest()); // מצפה ל-400
    }
    // --- השלמת שאר סוגי המדיניות ---
    @Test
    void setEventPolicy_WithAfterDatePolicy_Returns200Ok() throws Exception {
        String jsonBody = "{ \"type\": \"AFTER_DATE\", \"value\": \"2026-01-01\" }";
        mockMvc.perform(put("/api/events/event-123/policies")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk());
    }

    @Test
    void setEventPolicy_WithMaxTicketsPolicy_Returns200Ok() throws Exception {
        String jsonBody = "{ \"type\": \"MAX_TICKETS_PER_USER\", \"value\": 5 }";
        mockMvc.perform(put("/api/events/event-123/policies")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk());
    }

    @Test
    void setEventPolicy_WithMinTicketsPolicy_Returns200Ok() throws Exception {
        String jsonBody = "{ \"type\": \"MIN_TICKETS\", \"value\": 2 }";
        mockMvc.perform(put("/api/events/event-123/policies")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk());
    }

    // --- כיסוי לשגיאות טיפוסים (intValue / text) ---
    @Test
    void setEventPolicy_InvalidInt_ThrowsException() throws Exception {
        String jsonBody = "{ \"type\": \"MIN_AGE\", \"value\": \"לא-מספר\" }";
        mockMvc.perform(put("/api/events/event-123/policies")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setEventPolicy_BlankText_ThrowsException() throws Exception {
        String jsonBody = "{ \"type\": \"CODE\", \"value\": \"   \" }"; // טקסט ריק
        mockMvc.perform(put("/api/events/event-123/policies")
                .requestAttr("authenticatedMemberId", new MemberId("actor-123"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isBadRequest());
    }
}