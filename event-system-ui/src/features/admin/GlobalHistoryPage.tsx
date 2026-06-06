import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { adminApi } from '../../api/endpoints/admin';
import { formatDateTime, formatMoney } from '../../lib/format';
import '../../components/common.css';

export function GlobalHistoryPage() {
  const page = useQuery({
    queryKey: ['admin', 'history'],
    queryFn: () => adminApi.globalHistory(),
  });

  const rows = page.data?.items ?? [];

  return (
    <section>
      <Link to="/admin" className="btn ghost" style={{ marginBottom: '1rem' }}>← Admin</Link>
      <h1 className="page-title">Global purchase history</h1>

      {page.isLoading && <p>Loading…</p>}
      {page.data && rows.length === 0 && (
        <p className="empty">No purchases on the platform yet.</p>
      )}
      {rows.length > 0 && (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Receipt</th>
                <th>Buyer</th>
                <th>Event</th>
                <th>Purchased</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.recordId}>
                  <td><code>{r.recordId}</code></td>
                  <td><code style={{ fontSize: '0.75rem' }}>{r.buyerDisplayName ?? r.buyerId ?? '—'}</code></td>
                  <td>{r.eventSnapshot?.eventName ?? '—'}</td>
                  <td>{formatDateTime(r.purchaseTimestamp)}</td>
                  <td>{formatMoney(r.totalPaid.amount, r.totalPaid.currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {page.data && (
            <p className="meta" style={{ marginTop: '1rem' }}>
              Page {page.data.page + 1} of {Math.max(1, page.data.totalPages)} · {page.data.totalElements} total
            </p>
          )}
        </>
      )}
    </section>
  );
}
