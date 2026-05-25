package com.eventsystem.application.member;

import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.Notification;
import com.eventsystem.domain.member.NotificationType;
import com.eventsystem.domain.order.BuyerReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationService implements INotificationPort {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    
    private final MemberRepository memberRepository;

    private final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();

    public NotificationService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * This method should be called by the authentication/session management component
     * whenever a user logs in, to update their online status in the notification 
     * system. When a user comes online, any pending notifications will be dispatched immediately.
     */
    public void clientConnected(String memberId) {
        onlineUsers.add(memberId);
        dispatchDelayedNotifications(memberId);
    }

    /**
     * This method should be called by the authentication/session management component
     * whenever a user logs out, to update their online status in the notification
     * system.
     */
    public void clientDisconnected(String memberId) {
        onlineUsers.remove(memberId);
    }

    private void dispatchDelayedNotifications(String memberIdStr) {
        memberRepository.findById(new MemberId(memberIdStr)).ifPresent(member -> {
            List<Notification> pending = member.getUndeliveredNotifications();
            if (!pending.isEmpty()) {
                for (Notification n : pending) {
                    logger.info("Notification Dispatched_Delayed to {}: {}", memberIdStr, n.getContent());
                }
                member.markNotificationsDelivered();
                memberRepository.save(member);
            }
        });
    }

    private void dispatchMessage(BuyerReference buyer, NotificationType type, String message) {
        String memberIdStr = buyer.memberId();
        Notification notification = Notification.create(type, message);

        memberRepository.findById(new MemberId(memberIdStr)).ifPresentOrElse(member -> {
            member.addNotification(notification);
            
            if (onlineUsers.contains(memberIdStr)) {
                logger.info("Notification Dispatched_Realtime to {}: {}", memberIdStr, message);
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