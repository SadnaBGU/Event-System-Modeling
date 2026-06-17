package com.eventsystem.infrastructure.notifications;

import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.application.member.NotificationBroadcaster;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.Notification;
import com.eventsystem.domain.member.NotificationType;
import com.eventsystem.domain.order.BuyerReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationPortImpl implements INotificationPort {

    private static final Logger logger = LoggerFactory.getLogger(NotificationPortImpl.class);
    
    private final IMemberRepository memberRepository;

    // map memberId -> set of sessionIds to support multi-tab/multi-session
    private final ConcurrentHashMap<String, Set<String>> memberSessions = new ConcurrentHashMap<>();

    private final NotificationBroadcaster broadcaster;

    public NotificationPortImpl(IMemberRepository memberRepository) {
        this(memberRepository, null);
    }

    public NotificationPortImpl(IMemberRepository memberRepository, NotificationBroadcaster broadcaster) {
        this.memberRepository = memberRepository;
        this.broadcaster = broadcaster;
    }

    /**
     * This method should be called by the authentication/session management component
     * whenever a user logs in, to update their online status in the notification 
     * system. When a user comes online, any pending notifications will be dispatched immediately.
     */
    public void clientConnected(@NonNull String memberId) {
        clientConnected(memberId, "default");
    }

    public void clientConnected(@NonNull String memberId, String sessionId) {
        memberSessions.compute(memberId, (k, set) -> {
            if (set == null) 
                set = ConcurrentHashMap.newKeySet();
            set.add(sessionId);
            return set;
        });
        dispatchDelayedNotifications(memberId);
    }

    /**
     * This method should be called by the authentication/session management component
     * whenever a user logs out, to update their online status in the notification
     * system.
     */
    public void clientDisconnected(String memberId) {
        clientDisconnected(memberId, "default");
    }

    public void clientDisconnected(String memberId, String sessionId) {
        memberSessions.computeIfPresent(memberId, (k, set) -> {
            set.remove(sessionId);
            return set.isEmpty() ? null : set;
        });
    }

    private void dispatchDelayedNotifications(@NonNull String memberIdStr) {
        memberRepository.findById(new MemberId(memberIdStr)).ifPresent(member -> {
            List<Notification> pending = member.getUndeliveredNotifications();
            if (!pending.isEmpty()) {
                for (Notification n : pending) {
                    logger.info("Notification Dispatched_Delayed to {}: {}", memberIdStr, n.getContent());
                    if (broadcaster != null) {
                        broadcaster.broadcastToUser(memberIdStr, n);
                    }
                }
                member.markNotificationsDelivered();
                memberRepository.save(member);
            }
        });
    }

    @SuppressWarnings("null")
    private void dispatchMessage(BuyerReference buyer, NotificationType type, String message) {
        String memberIdStr = buyer.memberId();
        Notification notification = Notification.create(type, message);

        memberRepository.findById(new MemberId(memberIdStr)).ifPresentOrElse(member -> {
            member.addNotification(notification);
            
            boolean isOnline = memberSessions.containsKey(memberIdStr);
            if (isOnline) {
                logger.info("Notification Dispatched_Realtime to {}: {}", memberIdStr, message);
                if (broadcaster != null) {
                    broadcaster.broadcastToUser(memberIdStr, notification);
                }
                member.markNotificationsDelivered();
            } else {
                logger.info("Notification Saved for offline user {}", memberIdStr);
            }
            
            memberRepository.save(member);
            
        }, () -> logger.warn("Member {} not found. Could not send notification.", memberIdStr));
    }

    @Override
    public void sendPurchaseSuccess(BuyerReference buyer, String receiptId) {
        dispatchMessage(buyer, NotificationType.PURCHASE_COMPLETED, "Purchase successful. Receipt: " + receiptId);
    }

    @Override
    public void sendPurchaseFailure(BuyerReference buyer, String reason) {
        dispatchMessage(buyer, NotificationType.PURCHASE_FAILED, "Purchase failed: " + reason);
    }

    @Override
    public void sendQueueTurnArrived(BuyerReference buyer, String eventId) {
        dispatchMessage(buyer, NotificationType.QUEUE_TURN_ARRIVED, "Your turn has arrived for event " + eventId);
    }

    @Override
    public void sendEventSoldOut(BuyerReference buyer, String eventId) {
        dispatchMessage(buyer, NotificationType.SOLD_OUT, "Event " + eventId + " is sold out.");
    }
}