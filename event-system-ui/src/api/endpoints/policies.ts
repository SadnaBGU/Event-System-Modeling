import { api } from '../client';
import type { PolicyBundle } from '../../types/policies';

export const policiesApi = {
  getCompany: (companyId: string) =>
    api.get<PolicyBundle>(`/companies/${companyId}/policies`).then((r) => r.data),

  putCompany: (companyId: string, body: PolicyBundle) =>
    api.put<PolicyBundle>(`/companies/${companyId}/policies`, body).then((r) => r.data),

  getEvent: (eventId: string) =>
    api.get<PolicyBundle>(`/events/${eventId}/policies`).then((r) => r.data),

  putEvent: (eventId: string, body: PolicyBundle) =>
    api.put<PolicyBundle>(`/events/${eventId}/policies`, body).then((r) => r.data),
};
