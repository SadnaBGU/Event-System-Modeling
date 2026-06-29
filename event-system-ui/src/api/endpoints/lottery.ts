import { api } from '../client';
import type { LotteryRegistrationRequest } from '../../types/api';

export interface LotteryStatusDto {
  exists: boolean;
  status: 'REGISTRATION_OPEN' | 'CLOSED' | 'DRAWN' | null;
  registrationDeadline: string | null;
}

export interface OpenLotteryRequest {
  registrationDeadline: string;
}

export interface LotteryWinnerDto {
  memberId: string;
  username: string;
  codeExpiry: string;
}

// Backend returns 201 with empty body for registration.
export const lotteryApi = {
  /** Participant: enter the lottery for an event. */
  register: (eventId: string, body: LotteryRegistrationRequest = {}) =>
    api
      .post<void>(`/events/${eventId}/lottery/registrations`, body)
      .then(() => undefined),

  /** Organiser: open a lottery for an event (requires event-management permission). */
  open: (eventId: string, body: OpenLotteryRequest) =>
    api
      .post<{ lotteryId: string; status: string; registrationDeadline: string }>(
        `/events/${eventId}/lottery`,
        body,
      )
      .then((r) => r.data),

  /** Organiser: close registration and draw winners (requires event-management permission). */
  draw: (eventId: string, winnerCount: number) =>
    api
      .post<{ lotteryId: string; status: string; winners: number }>(
        `/events/${eventId}/lottery/draw`,
        { winnerCount },
      )
      .then((r) => r.data),

  /** Public: whether a lottery exists for an event and its current status. */
  status: (eventId: string) =>
    api.get<LotteryStatusDto>(`/events/${eventId}/lottery`).then((r) => r.data),

  /** Organiser: list the winners of a drawn lottery (requires event-management permission). */
  winners: (eventId: string) =>
    api.get<LotteryWinnerDto[]>(`/events/${eventId}/lottery/winners`).then((r) => r.data),
};
