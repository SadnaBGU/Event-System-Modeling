import { api } from '../client';
import type { CreateEventRequest } from '../../types/api';

export const eventsMgmtApi = {
  // Backend returns 201 Created with empty body and Location header.
  create: (companyId: string, body: CreateEventRequest) =>
    api.post<void>(`/companies/${companyId}/events`, body).then((r) => r.headers.location ?? ''),
};
