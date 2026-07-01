import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { companiesApi } from '../../api/endpoints/companies';
import { useAuthStore } from '../../auth/authStore';
import '../../components/common.css';

export function CompaniesListPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const session = useAuthStore((s) => s.session);
  const list = useQuery({ queryKey: ['companies'], queryFn: () => companiesApi.list() });
  const invitations = useQuery({
    queryKey: ['invitations'],
    queryFn: () => companiesApi.invitations(),
    enabled: !!session,
  });
  const accept = useMutation({
    mutationFn: (companyId: string) => companiesApi.acceptInvitation(companyId, session!.memberId),
    onSuccess: () => {
      toast.success('Appointment accepted. Your access was updated.');
      qc.invalidateQueries({ queryKey: ['invitations'] });
      qc.invalidateQueries({ queryKey: ['companies'] });
    },
  });
  const [name, setName] = useState('');
  const [contact, setContact] = useState('');

  const create = useMutation({
    mutationFn: () =>
      companiesApi.create({
        companyName: name,
        // Backend's CompanyController maps `contactDetails` to the company's required
        // `description` field, so we must always send a non-empty string.
        contactDetails: contact || `${name} — production company`,
      }),
    onSuccess: (company) => {
      toast.success('Company created successfully.');
      setName('');
      setContact('');
      // The creator becomes the company owner on the backend; company-scoped screens
      // resolve permissions from the backend roles, so no client-side role is needed.
      qc.invalidateQueries({ queryKey: ['companies'] });
      if (company.companyId) navigate(`/companies/${company.companyId}`);
    },
  });

  return (
    <section>
      <h1 className="page-title">Companies</h1>
      <p className="meta" style={{ marginTop: '-0.75rem', marginBottom: '1.5rem' }}>
        You only see companies you own or manage. Create one below to get started.
      </p>

      {invitations.data && invitations.data.length > 0 && (
        <div className="panel" style={{ marginBottom: '1.5rem' }}>
          <h2 style={{ fontSize: '1.05rem', margin: '0 0 0.5rem' }}>Pending invitations</h2>
          <p className="meta" style={{ marginTop: 0 }}>
            You've been invited to a role. Accept to gain its permissions and see the company.
          </p>
          <table className="table">
            <thead>
              <tr>
                <th>Company</th>
                <th>Role</th>
                <th aria-label="actions" />
              </tr>
            </thead>
            <tbody>
              {invitations.data.map((inv) => (
                <tr key={inv.companyId}>
                  <td style={{ fontWeight: 500 }}>{inv.companyName}</td>
                  <td><code>{inv.roleType}</code></td>
                  <td style={{ textAlign: 'right' }}>
                    <button
                      type="button"
                      className="btn"
                      disabled={accept.isPending}
                      onClick={() => accept.mutate(inv.companyId)}
                    >
                      Accept
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {list.isLoading && <p className="meta">Loading…</p>}
      {list.data && list.data.length === 0 && (
        <p className="empty">
          You don't own or manage any companies yet.<br />
          Create one below — you'll become its owner automatically.
        </p>
      )}
      {list.data && list.data.length > 0 && (
        <table className="table" style={{ marginBottom: '2rem' }}>
          <thead>
            <tr>
              <th>Name</th>
              <th>Status</th>
              <th aria-label="actions" />
            </tr>
          </thead>
          <tbody>
            {list.data.map((c) => (
              <tr key={c.companyId}>
                <td style={{ fontWeight: 500 }}>{c.companyName}</td>
                <td><span className={`pill ${c.status}`}>{c.status}</span></td>
                <td style={{ textAlign: 'right' }}>
                  <Link to={`/companies/${c.companyId}`} className="btn ghost">
                    Manage
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="panel" style={{ maxWidth: 480 }}>
        <h2 style={{ fontSize: '1.05rem', margin: '0 0 0.75rem' }}>Create a new company</h2>
        <form
          className="form-stack"
          onSubmit={(e) => {
            e.preventDefault();
            if (!name) return;
            create.mutate();
          }}
        >
          <label>
            Company name
            <input value={name} onChange={(e) => setName(e.target.value)} required />
          </label>
          <label>
            Contact details (optional)
            <input
              value={contact}
              onChange={(e) => setContact(e.target.value)}
              placeholder="Email, phone, or short description"
            />
          </label>
          <button type="submit" className="btn success" disabled={create.isPending}>
            {create.isPending ? 'Creating…' : 'Create company'}
          </button>
        </form>
      </div>
    </section>
  );
}
