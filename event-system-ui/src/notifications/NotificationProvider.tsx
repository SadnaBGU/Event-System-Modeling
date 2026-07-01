import { useEffect } from 'react';
import { toast } from 'sonner';
import { useAuthStore } from '../auth/authStore';
import { notificationClient } from '../api/ws';
import { useNotificationStore } from './notificationStore';
import { router } from '../routes/router';
import type { NotificationDto } from '../types/api';

function toastMessageForNotification(n: NotificationDto): string {
    switch (n.type) {
        case 'EVENT_CANCELLED':
            return 'An event was cancelled. Open your notifications for full details.';
        case 'QUEUE_ADMITTED':
        case 'QUEUE_TURN_ARRIVED':
            return 'It is your turn in the virtual queue. Taking you to checkout now…';
        case 'ORDER_CONFIRMED':
            return 'Your purchase was confirmed successfully.';
        case 'LOTTERY_WON':
            return 'Great news: you won the lottery for an event.';
        case 'LOTTERY_LOST':
            return 'Lottery results are in: you were not selected this time.';
        case 'ACCOUNT_SUSPENDED':
            return 'Your account was suspended. Contact support or an administrator for help.';
        default:
            return 'You received a new notification.';
    }
}

/**
 * The backend queue-turn notification carries the event id inside its message
 * (e.g. "Your turn has arrived for event <eventId>"). We pull it back out so we can
 * send the admitted user straight to the event's queue page, which then opens their
 * order automatically.
 */
function extractEventId(message: string): string | undefined {
    const match = /for event\s+(\S+)/i.exec(message ?? '');
    return match?.[1]?.trim() || undefined;
}

function handleQueueTurnArrived(n: NotificationDto) {
    const eventId = (n.meta?.eventId as string | undefined) ?? extractEventId(n.message);
    if (!eventId) return;

    const target = `/events/${eventId}/queue`;
    // Avoid redundant navigation if the user is already on the queue/checkout flow.
    const current = window.location.pathname;
    if (current === target || current.startsWith('/orders/')) return;

    void router.navigate(target);
}

export function NotificationProvider({ children }: { children: React.ReactNode }) {
    const token = useAuthStore((s) => s.session?.token);
    const add = useNotificationStore((s) => s.add);

    useEffect(() => {
        if (!token) return;
        notificationClient.connect(token, (n) => {
            add(n);
            toast.info(toastMessageForNotification(n));
            if (n.type === 'QUEUE_TURN_ARRIVED' || n.type === 'QUEUE_ADMITTED') {
                handleQueueTurnArrived(n);
            }
        });
        return () => notificationClient.disconnect();
    }, [token, add]);

    return <>{children}</>;
}

