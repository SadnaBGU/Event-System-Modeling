import { Client, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { NotificationDto } from '../types/api';

const WS_URL = import.meta.env.VITE_WS_URL ?? '/api/notifications/stream';
const DESTINATION = import.meta.env.VITE_WS_DESTINATION ?? '/user/queue/notifications';
const TRANSPORT = (import.meta.env.VITE_WS_TRANSPORT ?? 'stomp') as 'stomp' | 'raw';

export interface NotificationClient {
    connect(token: string, onMessage: (n: NotificationDto) => void): void;
    disconnect(): void;
}

// Backend sends { notificationId, type, content, ... }; map it to the UI's { id, message, ... }.
function normalizeNotification(raw: unknown): NotificationDto {
    const r = (raw ?? {}) as Record<string, unknown>;
    const newId =
        globalThis.crypto && 'randomUUID' in globalThis.crypto
            ? globalThis.crypto.randomUUID()
            : String(Date.now());
    return {
        id: String(r.id ?? r.notificationId ?? newId),
        type: (r.type as NotificationDto['type']) ?? 'GENERIC',
        message: String(r.message ?? r.content ?? ''),
        createdAt: String(r.createdAt ?? new Date().toISOString()),
        meta: r.meta as Record<string, unknown> | undefined,
    };
}

class StompNotificationClient implements NotificationClient {
    private client: Client | null = null;
    private subscription: StompSubscription | null = null;

    connect(token: string, onMessage: (n: NotificationDto) => void) {
        this.disconnect();
        const client = new Client({
            webSocketFactory: () => new SockJS(WS_URL) as unknown as WebSocket,
            connectHeaders: { Authorization: `Bearer ${token}` },
            reconnectDelay: 5000,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
            onConnect: () => {
                this.subscription = client.subscribe(DESTINATION, (frame) => {
                    try {
                        const payload = normalizeNotification(JSON.parse(frame.body));
                        onMessage(payload);
                    } catch {
                        // ignore malformed frames; backend should always send valid JSON
                    }
                });
            },
            onStompError: (frame) => {
                // surfaces broker-level errors (e.g. auth failed) to the dev console
                console.error('STOMP error', frame.headers['message'], frame.body);
            },
        });
        client.activate();
        this.client = client;
    }

    disconnect() {
        try {
            this.subscription?.unsubscribe();
        } catch {
            // already gone
        }
        this.subscription = null;
        if (this.client) {
            void this.client.deactivate();
            this.client = null;
        }
    }
}


class RawWsNotificationClient implements NotificationClient {
    private socket: WebSocket | null = null;
    private reconnectTimer: number | null = null;
    private intentionallyClosed = false;

    connect(token: string, onMessage: (n: NotificationDto) => void) {
        this.disconnect();
        this.intentionallyClosed = false;
        const url = new URL(WS_URL, window.location.origin);
        url.protocol = url.protocol.replace('http', 'ws');
        url.searchParams.set('token', token);
        const open = () => {
            const ws = new WebSocket(url.toString());
            ws.onmessage = (event) => {
                try {
                    const payload = normalizeNotification(JSON.parse(event.data));
                    onMessage(payload);
                } catch {
                    // ignore
                }
            };
            ws.onclose = () => {
                if (this.intentionallyClosed) return;
                this.reconnectTimer = window.setTimeout(open, 5000);
            };
            this.socket = ws;
        };
        open();
    }

    disconnect() {
        this.intentionallyClosed = true;
        if (this.reconnectTimer !== null) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
        this.socket?.close();
        this.socket = null;
    }
}

export const notificationClient: NotificationClient =
    TRANSPORT === 'raw' ? new RawWsNotificationClient() : new StompNotificationClient();
