package com.eventsystem.infrastructure.api.notifications;

import com.eventsystem.domain.member.HashedCredentials;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.PersonalDetails;
import com.eventsystem.infrastructure.security.JwtTokenService;
import com.eventsystem.application.member.IMemberRepository;
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
public class NotificationsRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IMemberRepository memberRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Test
    public void pending_returnsNotifications_and_marksDelivered() throws Exception {
        MemberId id = new MemberId("notif-member-1");
        var hashed = new HashedCredentials("h","s","bcrypt");
        var details = new PersonalDetails("Test","User","t@e.local", java.time.LocalDate.of(1990,1,1));
        Member m = new Member(id, id.value(), hashed, details);
        // add an undelivered notification
        m.addNotification(com.eventsystem.domain.member.Notification.create(com.eventsystem.domain.member.NotificationType.PURCHASE_COMPLETED, "ok"));
        memberRepository.save(m);

        String token = tokenService.issueToken(id, Duration.ofMinutes(10));

        mockMvc.perform(get("/api/notifications/pending").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications").isArray())
                .andExpect(jsonPath("$.notifications[0].content").value("ok"));

        // subsequent call should return empty because we marked delivered by default
        mockMvc.perform(get("/api/notifications/pending").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications").isEmpty());
    }
}
