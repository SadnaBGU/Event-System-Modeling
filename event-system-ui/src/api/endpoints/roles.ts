import { api } from '../client';
import type { AppointRoleRequest, CompanyRoleDto } from '../../types/api';

// Backend has no GET /companies/{id}/roles. list() returns empty so the page renders.
export const rolesApi = {
  list: async (_companyId: string): Promise<CompanyRoleDto[]> => {
    void _companyId;
    return [];
  },

  appoint: (companyId: string, body: AppointRoleRequest) =>
    api
      .post<void>(`/companies/${companyId}/roles`, body)
      .then(() => undefined),

  remove: (companyId: string, targetMemberId: string) =>
    api
      .delete<void>(`/companies/${companyId}/roles/${targetMemberId}`)
      .then(() => undefined),
};
