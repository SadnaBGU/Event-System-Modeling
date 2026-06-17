package com.eventsystem.infrastructure.security;

import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.infrastructure.persistence.springrepostests.BasePostgresTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc
public class AuthenticationInterceptorIntegrationTest extends BasePostgresTest{

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private IMemberRepository memberRepository;

    @Test
    public void missingAuthorizationHeader_returns401() throws Exception {
        String target = "member-missing-1";
        mockMvc.perform(get("/api/members/" + target))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorType").value("AuthenticationException"))
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/members/" + target));
    }

    @Test
    public void invalidToken_returns401() throws Exception {
        String target = "member-invalid-1";
        mockMvc.perform(get("/api/members/" + target).header("Authorization", "Bearer bad.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorType").value("AuthenticationException"))
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID"));
    }

    @Test
    public void validToken_allowsRequestAndInjectsMemberId() throws Exception {
        MemberId id = new MemberId("member-test-1");
        // ensure member exists with personal details (MemberDto serialization expects them)
        var hashed = new com.eventsystem.domain.member.HashedCredentials("h","s","bcrypt");
        var details = new com.eventsystem.domain.member.PersonalDetails(java.time.LocalDate.of(1990,1,1), "test@example.local", "Test", "User");
        memberRepository.save(new Member(id, id.value(), hashed, details));

        String token = tokenService.issueToken(id, Duration.ofMinutes(10));

        mockMvc.perform(get("/api/members/" + id.value()).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberId.value").value(id.value()));
    }
}
