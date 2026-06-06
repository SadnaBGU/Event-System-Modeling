import { api } from '../client';
import type { PurchaseRecordDto } from '../../types/api';

export const historyApi = {
  list: () =>
    api.get<PurchaseRecordDto[]>('/history/receipts').then((r) => r.data),

  get: (recordId: string) =>
    api.get<PurchaseRecordDto>(`/history/receipts/${recordId}`).then((r) => r.data),
};
