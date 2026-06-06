import { api } from '../client';
import type {
  GlobalHistoryPage,
  SuspendRequest,
  SuspensionDto,
} from '../../types/api';

export const adminApi = {
  // Companies
  closeCompany: (companyId: string) =>
    api.delete<void>(`/admin/companies/${companyId}`).then(() => undefined),

  // Suspensions — backend AdminController
  suspend: (memberId: string, body: SuspendRequest) =>
    api
      .post<void>(`/admin/members/${memberId}/suspensions`, body)
      .then(() => undefined),

  unsuspend: (memberId: string) =>
    api
      .delete<void>(`/admin/members/${memberId}/suspensions`)
      .then(() => undefined),

  // Backend lives at /admin/suspensions (not /admin/members/suspensions).
  listSuspensions: () =>
    api.get<SuspensionDto[]>('/admin/suspensions').then((r) => r.data),

  // Global purchase history — paginated envelope from AdminStreamController.
  globalHistory: (params: { page?: number; size?: number } = {}) =>
    api.get<GlobalHistoryPage>('/admin/history', { params }).then((r) => r.data),
};
