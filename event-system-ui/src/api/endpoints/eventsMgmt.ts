import { api } from '../client';
import type { CreateEventRequest, EventDetailDto } from '../../types/api';

export const eventsMgmtApi = {
  create: (companyId: string, body: CreateEventRequest) =>
    api.post<EventDetailDto>(`/companies/${companyId}/events`, body).then((r) => r.data),
};
