import { create } from 'zustand';
import type { NotificationDto } from '../types/api';

interface NotificationState {
    items: NotificationDto[];
    unread: number;
    add: (n: NotificationDto) => void;
    markAllRead: () => void;
    clear: () => void;
}

export const useNotificationStore = create<NotificationState>((set) => ({
    items: [],
    unread: 0,
    add: (n) =>
        set((s) => ({
            items: [n, ...s.items].slice(0, 100),
            unread: s.unread + 1,
        })),
    markAllRead: () => set({ unread: 0 }),
    clear: () => set({ items: [], unread: 0 }),
}));
