import { api } from '../client';
import type { AppointRoleRequest, CompanyRoleDto } from '../../types/api';

export const rolesApi = {
  list: (companyId: string) =>
    api.get<CompanyRoleDto[]>(`/companies/${companyId}/roles`).then((r) => r.data),

  appoint: (companyId: string, body: AppointRoleRequest) =>
    api.post<CompanyRoleDto>(`/companies/${companyId}/roles`, body).then((r) => r.data),

  remove: (companyId: string, targetMemberId: string) =>
    api
      .delete<void>(`/companies/${companyId}/roles/${targetMemberId}`)
      .then((r) => r.data),
};
