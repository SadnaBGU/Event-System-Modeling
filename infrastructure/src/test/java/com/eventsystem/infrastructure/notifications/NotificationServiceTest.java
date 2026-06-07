package com.eventsystem.infrastructure.notifications;

import com.eventsystem.domain.member.*;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.infrastructure.notifications.NotificationPortImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private IMemberRepository memberRepository;

    @InjectMocks
    private NotificationPortImpl notificationService;

    private Member testMember;
    private BuyerReference testBuyer;
    private final String MEMBER_ID = "user-123";

    @BeforeEach
    void setUp() {
        testBuyer = new BuyerReference(BuyerType.MEMBER, "sess-1", MEMBER_ID);
        testMember = new Member(new MemberId(MEMBER_ID));
    }

    @Test
    void dispatch_UserOnline_SendsRealtimeAndMarksDelivered() {
        // Arrange
        when(memberRepository.findById(new MemberId(MEMBER_ID))).thenReturn(Optional.of(testMember));
        
        // Act
        notificationService.clientConnected(MEMBER_ID);
        notificationService.sendPurchaseSuccess(testBuyer, "REC-123");

        // Assert
        verify(memberRepository, times(1)).save(testMember);
        
        assertEquals(1, testMember.getNotificationInbox().size());
        assertTrue(testMember.getNotificationInbox().get(0).isDelivered());
    }

    @Test
    void dispatch_UserOffline_SavesAsUndelivered() {
        // Arrange
        when(memberRepository.findById(new MemberId(MEMBER_ID))).thenReturn(Optional.of(testMember));
        
        // Act
        notificationService.sendPurchaseFailure(testBuyer, "Card declined");

        // Assert
        verify(memberRepository, times(1)).save(testMember);
        
        assertEquals(1, testMember.getNotificationInbox().size());
        assertFalse(testMember.getNotificationInbox().get(0).isDelivered());
    }

    @Test
    void clientConnected_WhenLoggingIn_SendsDelayedNotifications() {
        // Arrange
        Notification delayedNotif = Notification.create(NotificationType.SOLD_OUT, "Event Sold Out");
        testMember.addNotification(delayedNotif);
        
        when(memberRepository.findById(new MemberId(MEMBER_ID))).thenReturn(Optional.of(testMember));

        // Act
        notificationService.clientConnected(MEMBER_ID);

        // Assert
        verify(memberRepository, times(1)).save(testMember);
        
        assertTrue(delayedNotif.isDelivered());
    }
}