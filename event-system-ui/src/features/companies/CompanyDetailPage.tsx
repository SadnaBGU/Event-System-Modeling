import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { companiesApi } from '../../api/endpoints/companies';
import { formatMoney } from '../../lib/format';
import '../../components/common.css';

export function CompanyDetailPage() {
  const { companyId = '' } = useParams();
  const qc = useQueryClient();

  // Backend has no GET /companies/{id} yet — companiesApi.get() resolves to null.
  // The page falls back to showing actions against the URL-bound id until that endpoint exists.
  const company = useQuery({
    queryKey: ['company', companyId],
    queryFn: () => companiesApi.get(companyId),
    enabled: !!companyId,
  });

  const sales = useQuery({
    queryKey: ['sales', companyId],
    queryFn: () => companiesApi.salesReport(companyId),
    enabled: !!companyId,
  });

  const setStatus = useMutation({
    mutationFn: (status: 'ACTIVE' | 'SUSPENDED') =>
      companiesApi.updateStatus(companyId, { status }),
    onSuccess: () => {
      toast.success('Status updated');
      qc.invalidateQueries({ queryKey: ['company', companyId] });
      qc.invalidateQueries({ queryKey: ['companies'] });
    },
  });

  const c = company.data;
  return (
    <section>
      <Link to="/companies" className="btn ghost" style={{ marginBottom: '1rem' }}>← Companies</Link>
      <h1 className="page-title">{c?.companyName ?? 'Company'}</h1>
      <p className="meta">Company id: <code>{companyId}</code></p>
      {c ? (
        <>
          <p className="meta">Status: <code>{c.status}</code></p>
          {c.contactDetails && <p className="meta">{c.contactDetails}</p>}
        </>
      ) : (
        <p className="meta">Company details aren't yet exposed by the backend — actions still work.</p>
      )}

      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginTop: '1rem' }}>
        <Link to={`/companies/${companyId}/roles`} className="btn">Manage roles</Link>
        <Link to={`/companies/${companyId}/policies`} className="btn">Company policies</Link>
        <Link to={`/companies/${companyId}/events/new`} className="btn">New event</Link>
        <button
          type="button"
          className="btn ghost"
          onClick={() => setStatus.mutate('SUSPENDED')}
          disabled={setStatus.isPending}
        >
          Suspend
        </button>
        <button
          type="button"
          className="btn ghost"
          onClick={() => setStatus.mutate('ACTIVE')}
          disabled={setStatus.isPending}
        >
          Reactivate
        </button>
      </div>

      <h2 style={{ fontSize: '1.05rem', marginTop: '1.5rem' }}>Sales report</h2>
      {sales.isLoading && <p>Loading…</p>}
      {sales.isError && <p className="empty">Could not load the sales report.</p>}
      {sales.data && sales.data.length === 0 && (
        <p className="empty">No sales yet.</p>
      )}
      {sales.data && sales.data.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Event</th>
              <th>Tickets sold</th>
              <th>Gross revenue</th>
            </tr>
          </thead>
          <tbody>
            {sales.data.map((row) => (
              <tr key={row.eventId}>
                <td>{row.eventName}</td>
                <td>{row.ticketsSold}</td>
                <td>{formatMoney(row.grossRevenue)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
