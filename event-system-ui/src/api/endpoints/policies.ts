import { api } from '../client';
import type {
  DiscountPolicySummary,
  PolicyBundle,
  PurchasePolicySummary,
} from '../../types/policies';
import type { PurchasePolicyTree } from './policyMappers';
import { purchaseNodeToTree } from './policyMappers';

// Backend GET now returns split arrays:
// {
//   purchasePolicies: [...],
//   discountPolicies: [...],
//   items: [...] // legacy field, intentionally ignored by the UI
// }
//
// The editable rule trees are still UI drafts only. Backend GET returns summaries,
// not the original editable tree structure.
interface PoliciesPayload {
  purchasePolicies?: PurchasePolicySummary[];
  discountPolicies?: DiscountPolicySummary[];

  // Legacy combined field. Do not use it, so backend can remove it later.
  items?: unknown[];
}

function emptyBundle(): PolicyBundle {
  return {
    discount: null,
    purchase: null,
    purchasePolicies: [],
    discountPolicies: [],
  };
}

async function getBundle(path: string): Promise<PolicyBundle> {
  try {
    const response = await api.get<PoliciesPayload>(path);
    const data = response.data ?? {};

    return {
      discount: null,
      purchase: null,
      purchasePolicies: Array.isArray(data.purchasePolicies)
        ? data.purchasePolicies
        : [],
      discountPolicies: Array.isArray(data.discountPolicies)
        ? data.discountPolicies
        : [],
    };
  } catch {
    // Ignore read failure — fall back to empty bundle so the editor can still open.
    return emptyBundle();
  }
}

export interface DiscountItemRequest {
  name: string;
  percent: number;
  /** Present => hidden coupon code. */
  code?: string;
  /** Present => conditional discount (min tickets in the order). */
  minTickets?: number;
  /** ISO date the offer ends, e.g. "2026-09-01". */
  endDate?: string;
}

export interface DiscountPolicyRequest {
  policyName?: string;
  stackable: boolean;
  discounts: DiscountItemRequest[];
}

export const policiesApi = {
  getCompany: (companyId: string) => getBundle(`/companies/${companyId}/policies`),
  getEvent: (eventId: string) => getBundle(`/events/${eventId}/policies`),

  putCompany: (companyId: string, body: PolicyBundle) => {
    const tree: PurchasePolicyTree | null = body.purchase
      ? purchaseNodeToTree(body.purchase)
      : null;

    if (!tree) return Promise.resolve(undefined);

    return api.put<void>(`/companies/${companyId}/policies`, tree)
      .then(() => undefined);
  },

  putEvent: (eventId: string, body: PolicyBundle) => {
    const tree: PurchasePolicyTree | null = body.purchase
      ? purchaseNodeToTree(body.purchase)
      : null;

    if (!tree) return Promise.resolve(undefined);

    return api.put<void>(`/events/${eventId}/policies`, tree)
      .then(() => undefined);
  },

  putCompanyDiscount: (companyId: string, body: DiscountPolicyRequest) =>
    api.put<void>(`/companies/${companyId}/discount-policies`, body)
      .then(() => undefined),

  putEventDiscount: (eventId: string, body: DiscountPolicyRequest) =>
    api.put<void>(`/events/${eventId}/discount-policies`, body)
      .then(() => undefined),

  deleteCompanyPurchase: (companyId: string, policyId: string) =>
  api.delete<void>(`/companies/${companyId}/policies/${policyId}`)
    .then(() => undefined),

  deleteEventPurchase: (eventId: string, policyId: string) =>
    api.delete<void>(`/events/${eventId}/policies/${policyId}`)
      .then(() => undefined),

  deleteCompanyDiscount: (companyId: string, policyId: string) =>
    api.delete<void>(`/companies/${companyId}/discount-policies/${policyId}`)
      .then(() => undefined),

  deleteEventDiscount: (eventId: string, policyId: string) =>
    api.delete<void>(`/events/${eventId}/discount-policies/${policyId}`)
      .then(() => undefined),
};