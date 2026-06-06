import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { adminApi } from '../../api/endpoints/admin';
import '../../components/common.css';

export function CloseCompanyPage() {
  const [companyId, setCompanyId] = useState('');
  const close = useMutation({
    mutationFn: () => adminApi.closeCompany(companyId),
    onSuccess: () => {
      toast.success('Company closed');
      setCompanyId('');
    },
  });

  return (
    <section>
      <Link to="/admin" className="btn ghost" style={{ marginBottom: '1rem' }}>← Admin</Link>
      <h1 className="page-title">Close a company</h1>
      <p className="meta">
        Closes a production company permanently. All future events are disabled.
      </p>
      <form
        className="form-stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (!companyId) return;
          if (!window.confirm(`Close company ${companyId}?`)) return;
          close.mutate();
        }}
      >
        <label>
          Company ID
          <input
            value={companyId}
            onChange={(e) => setCompanyId(e.target.value)}
            placeholder="CO-XXXXX"
            required
          />
        </label>
        <button type="submit" className="btn danger" disabled={close.isPending}>
          {close.isPending ? 'Closing…' : 'Close company'}
        </button>
      </form>
    </section>
  );
}
