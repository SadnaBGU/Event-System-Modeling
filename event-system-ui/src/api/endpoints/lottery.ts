import { api } from '../client';
import type { LotteryRegistrationRequest } from '../../types/api';

export interface LotteryStatusDto {
  exists: boolean;
  status: 'REGISTRATION_OPEN' | 'CLOSED' | 'DRAWN' | null;
}

// Backend returns 201 with empty body for registration.
export const lotteryApi = {
  /** Participant: enter the lottery for an event. */
  register: (eventId: string, body: LotteryRegistrationRequest = {}) =>
    api
      .post<void>(`/events/${eventId}/lottery/registrations`, body)
      .then(() => undefined),

  /** Organiser: open a lottery for an event (requires event-management permission). */
  open: (eventId: string) =>
    api.post<{ lotteryId: string; status: string }>(`/events/${eventId}/lottery`).then((r) => r.data),

  /** Public: whether a lottery exists for an event and its current status. */
  status: (eventId: string) =>
    api.get<LotteryStatusDto>(`/events/${eventId}/lottery`).then((r) => r.data),
};
