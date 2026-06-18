import { api } from '../client';
import type { EventDto, Page } from '../../types/api';

export interface EventSearchParams {
  artist?: string;
  date?: string; // YYYY-MM-DD
  priceRange?: string; // "min,max" or "min-max"
  page?: number;
  size?: number;
}

export interface UpdateEventRequest {
  eventName: string;
  dates: string[]; // ISO LocalDateTime[] (no zone, e.g. "2026-08-01T20:00:00")
  category?: string;
  location?: string;
  description?: string;
}

export type SeatStatus = 'AVAILABLE' | 'RESERVED' | 'SOLD';

export interface SeatDto {
  seatId: string;
  rowLabel: string;
  seatNumber: number;
  status: SeatStatus;
}

export interface ZoneSeatsDto {
  zoneId: string;
  zoneName: string;
  zoneType: 'SEATED' | 'STANDING';
  totalCapacity: number;
  availableCount: number;
  seats: SeatDto[];
}

export interface AddZoneRequest {
  zoneName: string;
  price: number;
  currency?: string;
  capacity: number;
  /** STANDING (default) or SEATED. Seated zones render a selectable seat map. */
  zoneType?: 'STANDING' | 'SEATED';
  /** Seated only: number of rows (A, B, …). */
  rows?: number;
  /** Seated only: seats per row. */
  seatsPerRow?: number;
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

  updateDetails: (eventId: string, body: UpdateEventRequest) =>
    api.put<void>(`/events/${eventId}/details`, body).then(() => undefined),

  zoneSeats: (zoneId: string) =>
    api.get<ZoneSeatsDto>(`/zones/${zoneId}/seats`).then((r) => r.data),
};
