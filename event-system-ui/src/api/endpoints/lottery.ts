import { api } from '../client';
import type { LotteryRegistrationRequest } from '../../types/api';

export const lotteryApi = {
  register: (eventId: string, body: LotteryRegistrationRequest = {}) =>
    api
      .post<{ registrationId: string }>(`/events/${eventId}/lottery/registrations`, body)
      .then((r) => r.data),
};
