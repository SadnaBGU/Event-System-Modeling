import { api } from '../client';
import type { QueueStatusDto } from '../../types/api';

export const queueApi = {
  enter: (eventId: string) =>
    api.post<void>(`/events/${eventId}/queue/entries`).then((r) => r.data),

  status: (eventId: string) =>
    api.get<QueueStatusDto>(`/events/${eventId}/queue/status`).then((r) => r.data),

  leave: (eventId: string) =>
    api.delete<void>(`/events/${eventId}/queue/admissions`).then((r) => r.data),
};
