import { api } from '../client';
import type {
  Page,
  PurchaseRecordDetailDto,
  PurchaseRecordSummaryDto,
} from '../../types/api';

export const historyApi = {
  list: () =>
    api
      .get<Page<PurchaseRecordSummaryDto>>('/history/receipts')
      .then((r) => r.data.items),

  listPaginated: (params: { page?: number; size?: number } = {}) =>
    api
      .get<Page<PurchaseRecordSummaryDto>>('/history/receipts', { params })
      .then((r) => r.data),

  get: (recordId: string) =>
    api.get<PurchaseRecordDetailDto>(`/history/receipts/${recordId}`).then((r) => r.data),
};
