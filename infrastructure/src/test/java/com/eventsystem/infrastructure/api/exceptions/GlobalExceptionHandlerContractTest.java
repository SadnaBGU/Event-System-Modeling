package com.eventsystem.infrastructure.api.exceptions;

import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventsystem.infrastructure.testsupport.InfrastructureSpringBootTest;

@InfrastructureSpringBootTest
@AutoConfigureMockMvc
public class GlobalExceptionHandlerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private IMemberRepository memberRepository;

    @Test
    public void crossMemberAccess_returns403_withErrorEnvelope() throws Exception {
        MemberId actor = new MemberId("actor-1");
        MemberId target = new MemberId("target-1");
        memberRepository.save(new Member(actor));
        memberRepository.save(new Member(target));

        String token = tokenService.issueToken(actor, Duration.ofMinutes(10));

        mockMvc.perform(get("/api/members/" + target.value()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorType").value("SecurityException"))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/members/" + target.value()));
    }
}
