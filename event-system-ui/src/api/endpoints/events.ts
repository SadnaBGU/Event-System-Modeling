import { api } from '../client';
import type { EventDto, Page } from '../../types/api';

export interface EventSearchParams {
  artist?: string;
  date?: string; // YYYY-MM-DD
  priceRange?: string; // "min,max" or "min-max"
  page?: number;
  size?: number;
}

export const eventsApi = {
  search: (params: EventSearchParams = {}) =>
    api.get<Page<EventDto>>('/events', { params }).then((r) => r.data.items),

  searchPaginated: (params: EventSearchParams = {}) =>
    api.get<Page<EventDto>>('/events', { params }).then((r) => r.data),

  get: (eventId: string) =>
    api.get<EventDto>(`/events/${eventId}`).then((r) => r.data),
};
