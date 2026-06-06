import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { historyApi } from '../../api/endpoints/history';
import { formatDateTime, formatMoney } from '../../lib/format';
import '../../components/common.css';

export function ReceiptDetailPage() {
  const { recordId = '' } = useParams();
  const q = useQuery({
    queryKey: ['receipt', recordId],
    queryFn: () => historyApi.get(recordId),
    enabled: !!recordId,
  });

  if (q.isLoading) return <p>Loading…</p>;
  if (q.isError || !q.data) return <p className="empty">Receipt not found.</p>;

  const r = q.data;
  return (
    <section>
      <Link to="/history" className="btn ghost" style={{ marginBottom: '1rem' }}>← All receipts</Link>
      <h1 className="page-title">Receipt {r.recordId}</h1>
      <p className="meta"><strong>{r.eventName}</strong></p>
      <p className="meta">Purchased {formatDateTime(r.purchaseDate)}</p>
      <p className="meta">Payment status: <code>{r.paymentStatus}</code></p>

      <table className="table" style={{ marginTop: '1rem' }}>
        <thead>
          <tr>
            <th>Zone</th>
            <th>Seat</th>
            <th>Price</th>
          </tr>
        </thead>
        <tbody>
          {r.tickets.map((it, idx) => (
            <tr key={`${it.zoneId}-${it.seatId}-${idx}`}>
              <td>{it.zoneId}</td>
              <td>{it.seatId}</td>
              <td>{formatMoney(it.price, r.currency)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="totals">
        <span>Total paid</span>
        <span>{formatMoney(r.totalAmount, r.currency)}</span>
      </div>
    </section>
  );
}
