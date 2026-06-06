import type {
  DiscountCondition,
  DiscountNode,
  PurchaseNode,
  PurchasePredicate,
} from '../../types/policies';

// ----- Discount preview -----
export function previewDiscount(node: DiscountNode | null): string {
  if (!node) return 'No discount.';
  switch (node.kind) {
    case 'simple':
      return `${node.percent}% off`;
    case 'conditional':
      return `${node.percent}% off when ${describeCondition(node.condition)}`;
    case 'coupon':
      return `${node.percent}% off with code "${node.code}" (until ${node.validUntil || 'no expiry'})`;
    case 'composite': {
      if (node.children.length === 0) return 'No discount (empty group).';
      const parts = node.children.map(previewDiscount);
      const joiner = node.op === 'MAX' ? ' OR (best of)' : ' AND ';
      const verb = node.op === 'MAX' ? 'whichever is best of: ' : 'all of: ';
      return parts.length === 1 ? parts[0] : `${verb}${parts.map((p) => `(${p})`).join(joiner)}`;
    }
  }
}

function describeCondition(c: DiscountCondition): string {
  switch (c.type) {
    case 'minTickets':
      return `buying at least ${c.value} ticket${c.value === 1 ? '' : 's'}`;
    case 'maxTickets':
      return `buying at most ${c.value} ticket${c.value === 1 ? '' : 's'}`;
    case 'dateRange': {
      if (c.from && c.until) return `purchased between ${c.from} and ${c.until}`;
      if (c.from) return `purchased on or after ${c.from}`;
      if (c.until) return `purchased on or before ${c.until}`;
      return 'within any date range';
    }
  }
}

// ----- Purchase preview -----
export function previewPurchase(node: PurchaseNode | null): string {
  if (!node) return 'Anyone can purchase.';
  switch (node.kind) {
    case 'predicate':
      return describePredicate(node.predicate);
    case 'composite': {
      if (node.children.length === 0) return 'Anyone can purchase (empty group).';
      const parts = node.children.map(previewPurchase);
      const joiner = node.op === 'AND' ? ' AND ' : ' OR ';
      return parts.length === 1 ? parts[0] : `(${parts.join(joiner)})`;
    }
  }
}

function describePredicate(p: PurchasePredicate): string {
  switch (p.type) {
    case 'minAge':
      return `age ≥ ${p.value}`;
    case 'minTickets':
      return `at least ${p.value} ticket${p.value === 1 ? '' : 's'}`;
    case 'maxTickets':
      return `at most ${p.value} ticket${p.value === 1 ? '' : 's'}`;
  }
}
