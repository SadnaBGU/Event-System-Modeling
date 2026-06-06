import { api } from '../client';
import type { PolicyBundle } from '../../types/policies';
import type { PurchasePolicyTree } from './policyMappers';
import { purchaseNodeToTree } from './policyMappers';

// Backend exposes only PUT for the purchase policy (single rule tree, no bundle wrapper).
// There is no GET endpoint and no discount-policy endpoint yet.
export const policiesApi = {
  getCompany: async (_companyId: string): Promise<PolicyBundle> => {
    void _companyId;
    return { discount: null, purchase: null };
  },

  putCompany: (companyId: string, body: PolicyBundle) => {
    const tree: PurchasePolicyTree | null = body.purchase ? purchaseNodeToTree(body.purchase) : null;
    if (!tree) return Promise.resolve(undefined);
    return api.put<void>(`/companies/${companyId}/policies`, tree).then(() => undefined);
  },

  getEvent: async (_eventId: string): Promise<PolicyBundle> => {
    void _eventId;
    return { discount: null, purchase: null };
  },

  putEvent: (eventId: string, body: PolicyBundle) => {
    const tree: PurchasePolicyTree | null = body.purchase ? purchaseNodeToTree(body.purchase) : null;
    if (!tree) return Promise.resolve(undefined);
    return api.put<void>(`/events/${eventId}/policies`, tree).then(() => undefined);
  },
};
