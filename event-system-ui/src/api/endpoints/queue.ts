import { api } from '../client';
import type { QueueStatusDto } from '../../types/api';

export const queueApi = {
  enter: (eventId: string, sessionId?: string) =>
    api.post<void>(`/events/${eventId}/queue/entries`, undefined, {
      params: sessionId ? { sessionId } : undefined,
    }).then((r) => r.data),

  status: (eventId: string, sessionId?: string) =>
    api.get<QueueStatusDto>(`/events/${eventId}/queue/status`, {
      params: sessionId ? { sessionId } : undefined,
    }).then((r) => r.data),

  leave: (eventId: string, sessionId?: string) =>
    api.delete<void>(`/events/${eventId}/queue/admissions`, {
      params: sessionId ? { sessionId } : undefined,
    }).then((r) => r.data),
};
