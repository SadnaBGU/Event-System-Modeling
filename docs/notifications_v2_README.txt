Notifications V2 — Real-time + Backlog

Overview
- Streams: STOMP over WebSocket at `/api/notifications/stream`.
- User-specific queue: server sends to `/user/{memberId}/queue/notifications`.
- Backlog endpoint: `GET /api/notifications/pending` with optional `markAsRead` query (default true) which atomically returns undelivered notifications and marks them delivered.

Files
- OpenAPI (YAML): `docs/api/notifications.yaml`
- OpenAPI JSON export: `docs/api/output/notifications.openapi.json`
- JSON Schemas: `docs/api/output/schemas/Notification.json`, `docs/api/output/schemas/NotificationsEnvelope.json`
- UI contract: `docs/api/notifications_ui_contract.md`

Notes for integrators
- Use STOMP subscription for real-time pushes and call the backlog endpoint on connect to reconcile missed messages.
- The backlog endpoint performs an atomic fetch-and-mark when `markAsRead=true` — server synchronizes on the `Member` aggregate to prevent duplicates across concurrent callers.
- `notificationId` is stable; UIs should de-duplicate on that key.
