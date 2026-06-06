import { api } from '../client';
import type { EventDetailDto, EventSummaryDto } from '../../types/api';

export interface EventSearchParams {
  artist?: string;
  date?: string;
  maxPrice?: number;
  page?: number;
  size?: number;
}

export const eventsApi = {
  search: (params: EventSearchParams = {}) =>
    api.get<EventSummaryDto[]>('/events', { params }).then((r) => r.data),

  get: (eventId: string) =>
    api.get<EventDetailDto>(`/events/${eventId}`).then((r) => r.data),
};
