package com.eventsystem.infrastructure.notifications;

import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.Notification;
import com.eventsystem.domain.member.NotificationType;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.eventsystem.application.member.NotificationBroadcaster;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPortImplTest {

    @Mock
    private IMemberRepository memberRepository;

    @Mock
    private NotificationBroadcaster broadcaster;

    @SuppressWarnings("null")
    @Test
    void sendPurchaseSuccess_whenMemberOnline_broadcastsAndMarksDelivered() {
        Member member = new Member(new MemberId("user-1"));
        when(memberRepository.findById(any())).thenReturn(Optional.of(member));

        NotificationPortImpl port = new NotificationPortImpl(memberRepository, broadcaster);

        // bring the member online
        port.clientConnected("user-1");

        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, "sess-1", "user-1");
        port.sendPurchaseSuccess(buyer, "receipt-123");

        // broadcaster should be invoked and member saved
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(broadcaster).broadcastToUser(eq("user-1"), cap.capture());
        verify(memberRepository, atLeastOnce()).save(member);

        // notifications should be marked delivered
        assertThat(member.getUndeliveredNotifications()).isEmpty();
        assertThat(cap.getValue().getContent()).contains("Purchase successful");
    }

    @SuppressWarnings("null")
    @Test
    void sendPurchaseSuccess_whenMemberOffline_savesUndeliveredNotification() {
        Member member = new Member(new MemberId("user-2"));
        when(memberRepository.findById(any())).thenReturn(Optional.of(member));

        NotificationPortImpl port = new NotificationPortImpl(memberRepository, broadcaster);

        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, "sess-2", "user-2");
        port.sendPurchaseSuccess(buyer, "receipt-xyz");

        // broadcaster should NOT be invoked
        verify(broadcaster, never()).broadcastToUser(anyString(), any());

        // repository should be asked to save the member with the queued notification
        verify(memberRepository).save(member);
        assertThat(member.getUndeliveredNotifications()).hasSize(1);
        assertThat(member.getUndeliveredNotifications().get(0).isDelivered()).isFalse();
    }

    @SuppressWarnings("null")
    @Test
    void clientConnected_dispatchesPendingNotifications() {
        Member member = new Member(new MemberId("user-3"));
        // add a pending notification
        member.addNotification(Notification.create(NotificationType.PURCHASE_COMPLETED, "delayed"));
        when(memberRepository.findById(any())).thenReturn(Optional.of(member));

        NotificationPortImpl port = new NotificationPortImpl(memberRepository, broadcaster);

        // when client connects, pending notifications should be broadcast
        port.clientConnected("user-3");

        verify(broadcaster).broadcastToUser(eq("user-3"), any(Notification.class));
        verify(memberRepository).save(member);
        assertThat(member.getUndeliveredNotifications()).isEmpty();
    }

    @Test
    void sendPurchaseSuccess_whenMemberMissing_noActionsTaken() {
        when(memberRepository.findById(any())).thenReturn(Optional.empty());

        NotificationPortImpl port = new NotificationPortImpl(memberRepository, broadcaster);

        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, "s", "missing-user");
        port.sendPurchaseSuccess(buyer, "r");

        verifyNoInteractions(broadcaster);
        verify(memberRepository, never()).save(any());
    }
}
