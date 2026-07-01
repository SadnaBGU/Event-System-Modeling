import { useEffect } from 'react';
import { toast } from 'sonner';
import { useAuthStore } from '../auth/authStore';
import { notificationClient } from '../api/ws';
import { useNotificationStore } from './notificationStore';
import type { NotificationDto } from '../types/api';

function toastMessageForNotification(n: NotificationDto): string {
    switch (n.type) {
        case 'EVENT_CANCELLED':
            return 'An event was cancelled. Open your notifications for full details.';
        case 'QUEUE_ADMITTED':
        case 'QUEUE_TURN_ARRIVED':
            return 'It is your turn in the virtual queue. You can continue to checkout now.';
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

export function NotificationProvider({ children }: { children: React.ReactNode }) {
    const token = useAuthStore((s) => s.session?.token);
    const add = useNotificationStore((s) => s.add);

    useEffect(() => {
        if (!token) return;
        notificationClient.connect(token, (n) => {
            add(n);
            toast.info(toastMessageForNotification(n));
        });
        return () => notificationClient.disconnect();
    }, [token, add]);

    return <>{children}</>;
}
