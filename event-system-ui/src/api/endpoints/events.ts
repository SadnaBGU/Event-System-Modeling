import { api } from '../client';
import type { EventDto, Page } from '../../types/api';

export interface EventSearchParams {
  artist?: string;
  date?: string; // YYYY-MM-DD
  priceRange?: string; // "min,max" or "min-max"
  page?: number;
  size?: number;
}

export interface AddZoneRequest {
  zoneName: string;
  price: number;
  currency?: string;
  capacity: number;
}

export const eventsApi = {
  search: (params: EventSearchParams = {}) =>
    api.get<Page<EventDto>>('/events', { params }).then((r) => r.data.items),

  searchPaginated: (params: EventSearchParams = {}) =>
    api.get<Page<EventDto>>('/events', { params }).then((r) => r.data),

  get: (eventId: string) =>
    api.get<EventDto>(`/events/${eventId}`).then((r) => r.data),

  publish: (eventId: string) =>
    api.post<void>(`/events/${eventId}/publish`).then(() => undefined),

  addZone: (eventId: string, body: AddZoneRequest) =>
    api.post<void>(`/events/${eventId}/zones`, body).then(() => undefined),
};
