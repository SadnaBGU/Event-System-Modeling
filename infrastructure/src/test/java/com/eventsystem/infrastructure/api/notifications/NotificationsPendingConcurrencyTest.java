package com.eventsystem.infrastructure.api.notifications;

import com.eventsystem.domain.member.HashedCredentials;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.PersonalDetails;
import com.eventsystem.domain.member.Notification;
import com.eventsystem.domain.member.NotificationType;
import com.eventsystem.infrastructure.security.JwtTokenService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventsystem.infrastructure.testsupport.InfrastructureSpringBootTest;


@InfrastructureSpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class NotificationsPendingConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IMemberRepository memberRepository;

    @Autowired
    private JwtTokenService tokenService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void concurrent_markAsRead_requests_are_atomic() throws Exception {
        MemberId id = new MemberId("concurrent-member-1");
        HashedCredentials hashed = new HashedCredentials("h","s","bcrypt");
        PersonalDetails details = new PersonalDetails(LocalDate.of(1990,1,1),"c@test.local", "C","Tester");
        Member m = new Member(id, id.value(), hashed, details);

        int notifications = 20;
        for (int i = 0; i < notifications; i++) {
            m.addNotification(Notification.create(NotificationType.PURCHASE_COMPLETED, "n" + i));
        }
        memberRepository.save(m);

        String token = tokenService.issueToken(id, Duration.ofMinutes(10));

        int callers = 5;
        ExecutorService ex = Executors.newFixedThreadPool(callers);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < callers; i++) {
            tasks.add(() -> {
                MvcResult mvcResult = mockMvc.perform(get("/api/notifications/pending?markAsRead=true").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
                String content = mvcResult.getResponse().getContentAsString();
                JsonNode root = mapper.readTree(content);
                JsonNode arr = root.path("notifications");
                return arr.size();
            });
        }

        List<Future<Integer>> results = ex.invokeAll(tasks);
        int totalReturned = 0;
        for (Future<Integer> f : results) {
            totalReturned += f.get();
        }

        // All notifications should have been returned exactly once across callers
        assertThat(totalReturned).isEqualTo(notifications);

        // subsequent call returns empty
        MvcResult mvcResult2 = mockMvc.perform(get("/api/notifications/pending").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root2 = mapper.readTree(mvcResult2.getResponse().getContentAsString());
        assertThat(root2.path("notifications")).isEmpty();

        // verify in repository that all notifications are marked delivered
        Member reloaded = memberRepository.findById(id).orElseThrow();
        assertThat(reloaded.getUndeliveredNotifications()).isEmpty();

        ex.shutdownNow();
    }
}
