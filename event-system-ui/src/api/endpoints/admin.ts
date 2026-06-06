import { api } from '../client';
import type {
  GlobalHistoryRow,
  SuspendRequest,
  SuspensionDto,
} from '../../types/api';

export const adminApi = {
  // Companies
  closeCompany: (companyId: string) =>
    api.delete<void>(`/admin/companies/${companyId}`).then((r) => r.data),

  // Members
  banMember: (memberId: string) =>
    api.delete<void>(`/admin/members/${memberId}`).then((r) => r.data),

  // Suspensions
  suspend: (memberId: string, body: SuspendRequest) =>
    api
      .post<SuspensionDto>(`/admin/members/${memberId}/suspensions`, body)
      .then((r) => r.data),

  unsuspend: (memberId: string) =>
    api
      .delete<void>(`/admin/members/${memberId}/suspensions`)
      .then((r) => r.data),

  listSuspensions: () =>
    api.get<SuspensionDto[]>('/admin/members/suspensions').then((r) => r.data),

  // Global history
  globalHistory: () =>
    api.get<GlobalHistoryRow[]>('/admin/history').then((r) => r.data),
};
