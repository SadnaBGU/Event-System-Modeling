import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { adminApi } from '../../api/endpoints/admin';
import { formatDateTime, formatMoney } from '../../lib/format';
import '../../components/common.css';

export function GlobalHistoryPage() {
  const list = useQuery({
    queryKey: ['admin', 'history'],
    queryFn: () => adminApi.globalHistory(),
  });

  return (
    <section>
      <Link to="/admin" className="btn ghost" style={{ marginBottom: '1rem' }}>← Admin</Link>
      <h1 className="page-title">Global purchase history</h1>

      {list.isLoading && <p>Loading…</p>}
      {list.data && list.data.length === 0 && (
        <p className="empty">No purchases on the platform yet.</p>
      )}
      {list.data && list.data.length > 0 && (
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
            {list.data.map((r) => (
              <tr key={r.recordId}>
                <td><code>{r.recordId}</code></td>
                <td>{r.buyerUsername}</td>
                <td>{r.eventName}</td>
                <td>{formatDateTime(r.purchasedAt)}</td>
                <td>{formatMoney(r.totalPaid)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
