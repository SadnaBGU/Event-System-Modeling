package com.eventsystem.infrastructure.config;

import org.springframework.context.ApplicationContext;
import com.eventsystem.domain.member.Notification;
import com.eventsystem.domain.member.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc
public class NotificationBroadcasterBeanTest {

    @Autowired
    private ApplicationContext context;

    @MockBean
    private SimpMessagingTemplate template;

    @Test
    public void broadcaster_sendsToUserQueue() throws Exception {
        Notification n = Notification.create(NotificationType.PURCHASE_COMPLETED, "payload");
        Object broadcaster = context.getBean("notificationBroadcaster");
        Method m = broadcaster.getClass().getMethod("broadcastToUser", String.class, com.eventsystem.domain.member.Notification.class);
        m.invoke(broadcaster, "user-1", n);

        verify(template).convertAndSendToUser(eq("user-1"), eq("/queue/notifications"), any());
    }
}
