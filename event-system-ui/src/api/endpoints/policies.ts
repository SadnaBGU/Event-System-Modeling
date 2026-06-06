import { api } from '../client';
import type { PolicyBundle } from '../../types/policies';
import type { PurchasePolicyTree } from './policyMappers';
import { purchaseNodeToTree } from './policyMappers';

// Backend exposes GET (read summary of active policies) and PUT (single rule tree).
// Discount policies are still write-only via that PUT, and the rule tree is the only thing
// the UI cares about, so the GET response is mainly used to know whether one exists.
interface PolicySummary { policyId: string; policyName: string; scope: string | null; summary: string | null }
interface PoliciesPayload { items: PolicySummary[] }

async function getBundle(path: string): Promise<PolicyBundle> {
  try {
    await api.get<PoliciesPayload>(path);
  } catch {
    // ignore read failure — fall back to empty bundle
  }
  return { discount: null, purchase: null };
}

export const policiesApi = {
  getCompany: (companyId: string) => getBundle(`/companies/${companyId}/policies`),
  getEvent: (eventId: string) => getBundle(`/events/${eventId}/policies`),

  putCompany: (companyId: string, body: PolicyBundle) => {
    const tree: PurchasePolicyTree | null = body.purchase ? purchaseNodeToTree(body.purchase) : null;
    if (!tree) return Promise.resolve(undefined);
    return api.put<void>(`/companies/${companyId}/policies`, tree).then(() => undefined);
  },

  putEvent: (eventId: string, body: PolicyBundle) => {
    const tree: PurchasePolicyTree | null = body.purchase ? purchaseNodeToTree(body.purchase) : null;
    if (!tree) return Promise.resolve(undefined);
    return api.put<void>(`/events/${eventId}/policies`, tree).then(() => undefined);
  },
};
