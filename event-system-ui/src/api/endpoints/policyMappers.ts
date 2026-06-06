import type { PurchaseNode } from '../../types/policies';

// Wire shape accepted by backend CompanyPolicyController.
// `value` is the rule's scalar (int for age/tickets), unused for AND/OR.
export type PurchasePolicyTree =
  | { type: 'AND' | 'OR'; operands: PurchasePolicyTree[] }
  | { type: 'MIN_AGE'; value: number }
  | { type: 'MIN_TICKETS_PER_USER'; value: number }
  | { type: 'MAX_TICKETS_PER_USER'; value: number };

export function purchaseNodeToTree(node: PurchaseNode): PurchasePolicyTree {
  if (node.kind === 'composite') {
    return { type: node.op, operands: node.children.map(purchaseNodeToTree) };
  }
  switch (node.predicate.type) {
    case 'minAge':
      return { type: 'MIN_AGE', value: node.predicate.value };
    case 'minTickets':
      return { type: 'MIN_TICKETS_PER_USER', value: node.predicate.value };
    case 'maxTickets':
      return { type: 'MAX_TICKETS_PER_USER', value: node.predicate.value };
  }
}
