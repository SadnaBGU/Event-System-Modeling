import { api } from '../client';
import type { LotteryRegistrationRequest } from '../../types/api';

// Backend returns 201 with empty body.
export const lotteryApi = {
  register: (eventId: string, body: LotteryRegistrationRequest = {}) =>
    api
      .post<void>(`/events/${eventId}/lottery/registrations`, body)
      .then(() => undefined),
};
