import { Server } from 'mock-socket';
import type { NotificationDto } from '../types/api';

let server: Server | null = null;

export function startMockNotificationServer(url = 'ws://localhost:5173/api/notifications/stream') {
    if (server) return;
    try {
        server = new Server(url);
    } catch {
        return;
    }
    server.on('connection', (socket) => {
        let i = 0;
        const interval = setInterval(() => {
            i += 1;
            const note: NotificationDto = {
                id: `notif-${i}-${Date.now()}`,
                type: 'GENERIC',
                message: `Mock notification #${i}`,
                createdAt: new Date().toISOString(),
            };
            socket.send(JSON.stringify(note));
        }, 15000);
        socket.on('close', () => clearInterval(interval));
    });
}

export function stopMockNotificationServer() {
    server?.stop();
    server = null;
}
