import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { historyApi } from '../../api/endpoints/history';
import { formatDateTime, formatMoney } from '../../lib/format';
import '../../components/common.css';

export function HistoryPage() {
  const list = useQuery({ queryKey: ['history'], queryFn: () => historyApi.list() });

  return (
    <section>
      <h1 className="page-title">My receipts</h1>
      {list.isLoading && <p>Loading…</p>}
      {list.data && list.data.length === 0 && (
        <p className="empty">You don't have any purchases yet.</p>
      )}
      {list.data && list.data.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Receipt</th>
              <th>Event</th>
              <th>Date</th>
              <th>Total</th>
              <th aria-label="actions" />
            </tr>
          </thead>
          <tbody>
            {list.data.map((r) => (
              <tr key={r.recordId}>
                <td><code>{r.recordId}</code></td>
                <td>{r.eventName}</td>
                <td>{formatDateTime(r.purchaseDate)}</td>
                <td>{formatMoney(r.totalAmount, r.currency)}</td>
                <td>
                  <Link to={`/history/${r.recordId}`} className="btn ghost">View</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
