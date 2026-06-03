import { useEffect } from 'react';
import { toast } from 'sonner';
import { useAuthStore } from '../auth/authStore';
import { notificationClient } from '../api/ws';
import { useNotificationStore } from './notificationStore';

export function NotificationProvider({ children }: { children: React.ReactNode }) {
    const token = useAuthStore((s) => s.session?.token);
    const add = useNotificationStore((s) => s.add);

    useEffect(() => {
        if (!token) return;
        notificationClient.connect(token, (n) => {
            add(n);
            toast(n.message);
        });
        return () => notificationClient.disconnect();
    }, [token, add]);

    return <>{children}</>;
}
