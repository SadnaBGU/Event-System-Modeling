// Composite policy tree shapes for V2 §2.a + appendix.
// Tree-of-nodes; matches WAF JSON until the team confirms a different normalized form.

export type DiscountNode =
  | { kind: 'simple'; percent: number }
  | { kind: 'conditional'; percent: number; condition: DiscountCondition }
  | { kind: 'coupon'; percent: number; code: string; validUntil: string }
  | { kind: 'composite'; op: 'MAX' | 'SUM'; children: DiscountNode[] };

export type DiscountCondition =
  | { type: 'minTickets'; value: number }
  | { type: 'maxTickets'; value: number }
  | { type: 'dateRange'; from?: string; until?: string };

export type PurchaseNode =
  | { kind: 'predicate'; predicate: PurchasePredicate }
  | { kind: 'composite'; op: 'AND' | 'OR'; children: PurchaseNode[] };

export type PurchasePredicate =
  | { type: 'minAge'; value: number }
  | { type: 'minTickets'; value: number }
  | { type: 'maxTickets'; value: number };

export interface PolicyScopeSummary {
  companyWide: boolean;
  eventIds: string[];
}

export interface PurchasePolicySummary {
  policyId: string;
  policyName: string;
  policyType: 'PURCHASE' | string;
  companyId: string;
  active: boolean;
  ownerType: 'COMPANY' | 'EVENT' | string;
  scope: PolicyScopeSummary | null;
  summary: string | null;
}

export interface DiscountInfoSummary {
  discountName: string;
  discountPercent: number | string;
  endDate?: string | null;

  // New fields from backend summary mapper
  discountType?: string | null;        // "Coupon" | "Visible" | "Conditional"
  conditionSummary?: string | null;    // e.g. "Coupon code: sale123"
  discountCode?: string | null;        // raw coupon code, visible to managers
  visible?: boolean;
}

export interface DiscountPolicySummary {
  policyId: string;
  policyName: string;
  policyType: 'DISCOUNT' | string;
  companyId: string;
  active: boolean;
  ownerType: 'COMPANY' | 'EVENT' | string;
  scope: PolicyScopeSummary | null;
  stackable: boolean;
  discounts: DiscountInfoSummary[];
  summary: string | null;
}

export interface PolicyBundle {
  // Editable draft tree. Currently only created from the UI, because backend GET
  // returns summaries/read models, not the original editable tree.
  discount: DiscountNode | null;
  purchase: PurchaseNode | null;

  // Read model returned by GET /companies/{id}/policies and /events/{id}/policies.
  purchasePolicies: PurchasePolicySummary[];
  discountPolicies: DiscountPolicySummary[];
}