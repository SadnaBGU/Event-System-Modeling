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
      <p className="meta">Purchased {formatDateTime(r.purchasedAt)}</p>

      <table className="table" style={{ marginTop: '1rem' }}>
        <thead>
          <tr>
            <th>Zone</th>
            <th>Seat</th>
            <th>Price</th>
          </tr>
        </thead>
        <tbody>
          {r.items.map((it) => (
            <tr key={`${it.zoneName}-${it.seatLabel}`}>
              <td>{it.zoneName}</td>
              <td>{it.seatLabel}</td>
              <td>{formatMoney(it.unitPrice)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="totals">
        <span>Total paid</span>
        <span>{formatMoney(r.totalPaid)}</span>
      </div>
    </section>
  );
}
