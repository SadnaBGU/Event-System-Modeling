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

export interface PolicyBundle {
  discount: DiscountNode | null;
  purchase: PurchaseNode | null;
}
