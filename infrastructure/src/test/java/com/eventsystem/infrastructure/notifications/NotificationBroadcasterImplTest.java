package com.eventsystem.infrastructure.notifications;

import com.eventsystem.domain.member.Notification;
import com.eventsystem.domain.member.NotificationType;
import com.eventsystem.infrastructure.api.notifications.NotificationDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationBroadcasterImplTest {

    @Mock
    private SimpMessagingTemplate template;

    @SuppressWarnings("null")
    @Test
    void broadcastToUser_sendsNotificationDtoToUserQueue() {
        NotificationBroadcasterImpl broadcaster = new NotificationBroadcasterImpl(template);
        Notification notification = new Notification(
                "notif-1",
                NotificationType.PURCHASE_COMPLETED,
                "Purchase successful",
                Instant.parse("2026-06-07T10:15:30Z"));

        broadcaster.broadcastToUser("user-1", notification);

        ArgumentCaptor<NotificationDto> dtoCaptor = ArgumentCaptor.forClass(NotificationDto.class);
        verify(template).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq("/queue/notifications"),
                dtoCaptor.capture());

        NotificationDto dto = dtoCaptor.getValue();
        assertThat(dto.notificationId()).isEqualTo("notif-1");
        assertThat(dto.type()).isEqualTo("PURCHASE_COMPLETED");
        assertThat(dto.content()).isEqualTo("Purchase successful");
        assertThat(dto.createdAt()).isEqualTo("2026-06-07T10:15:30Z");
        assertThat(dto.delivered()).isFalse();
    }
}