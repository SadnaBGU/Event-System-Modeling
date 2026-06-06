import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { companiesApi } from '../../api/endpoints/companies';
import '../../components/common.css';

export function CompaniesListPage() {
  const qc = useQueryClient();
  const list = useQuery({ queryKey: ['companies'], queryFn: () => companiesApi.list() });
  const [name, setName] = useState('');
  const [contact, setContact] = useState('');

  const create = useMutation({
    mutationFn: () => companiesApi.create({ companyName: name, contactDetails: contact || undefined }),
    onSuccess: () => {
      toast.success('Company created');
      setName('');
      setContact('');
      qc.invalidateQueries({ queryKey: ['companies'] });
    },
  });

  return (
    <section>
      <h1 className="page-title">Companies</h1>

      {list.isLoading && <p>Loading…</p>}
      {list.data && list.data.length === 0 && (
        <p className="empty">You don't manage any companies yet.</p>
      )}
      {list.data && list.data.length > 0 && (
        <table className="table" style={{ marginBottom: '1.5rem' }}>
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
                <td>{c.companyName}</td>
                <td><code>{c.status}</code></td>
                <td>
                  <Link to={`/companies/${c.companyId}`} className="btn ghost">
                    Manage
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2 style={{ fontSize: '1rem' }}>Create a new company</h2>
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
          <input value={contact} onChange={(e) => setContact(e.target.value)} />
        </label>
        <button type="submit" className="btn success" disabled={create.isPending}>
          {create.isPending ? 'Creating…' : 'Create company'}
        </button>
      </form>
    </section>
  );
}
