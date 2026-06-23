import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { companiesApi } from '../../api/endpoints/companies';
import { formatMoney } from '../../lib/format';
import '../../components/common.css';

export function CompanyDetailPage() {
  const { companyId = '' } = useParams();
  const qc = useQueryClient();

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

  if (company.isLoading) return <p>Loading…</p>;
  if (company.isError || !company.data) return <p className="empty">Company not found.</p>;

  const c = company.data;
  return (
    <section>
      <Link to="/companies" className="btn ghost" style={{ marginBottom: '1rem' }}>← Companies</Link>
      <h1 className="page-title">{c.companyName}</h1>
      <p className="meta">Status: <span className={`pill ${c.status}`}>{c.status}</span></p>
      {c.contactDetails && <p className="meta">{c.contactDetails}</p>}

      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginTop: '1rem' }}>
        <Link to={`/companies/${companyId}/roles`} className="btn">Manage roles</Link>
        <Link to={`/companies/${companyId}/policies`} className="btn">Company policies</Link>
        <Link to={`/companies/${companyId}/events/new`} className="btn">New event</Link>
        {c.status === 'ACTIVE' ? (
          <button
            type="button"
            className="btn ghost"
            onClick={() => setStatus.mutate('SUSPENDED')}
            disabled={setStatus.isPending}
          >
            Suspend
          </button>
        ) : c.status === 'SUSPENDED' ? (
          <button
            type="button"
            className="btn ghost"
            onClick={() => setStatus.mutate('ACTIVE')}
            disabled={setStatus.isPending}
          >
            Reactivate
          </button>
        ) : null}
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
