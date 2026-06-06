export function formatMoney(amount: number, currency?: string | null): string {
  const cur = currency && currency.length === 3 ? currency : 'USD';
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: cur,
    maximumFractionDigits: 2,
  }).format(amount);
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString();
}
