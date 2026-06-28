import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { rolesApi } from '../../api/endpoints/roles';
import {
  ALL_PERMISSIONS,
  type Permission,
  type RoleType,
} from '../../types/api';
import '../../components/common.css';

export function RolesPage() {
  const { companyId = '' } = useParams();
  const qc = useQueryClient();
  const list = useQuery({
    queryKey: ['roles', companyId],
    queryFn: () => rolesApi.list(companyId),
    enabled: !!companyId,
  });

  const [targetUsername, setTarget] = useState('');
  const [roleType, setRoleType] = useState<RoleType>('MANAGER');
  const [permissions, setPermissions] = useState<Permission[]>(['EVENT_INVENTORY_MANAGEMENT']);

  const appoint = useMutation({
    mutationFn: () =>
      rolesApi.appoint(companyId, {
        targetUsername,
        roleType,
        permissionsList: permissions,
      }),
    onSuccess: () => {
      toast.success('Role appointed');
      setTarget('');
      qc.invalidateQueries({ queryKey: ['roles', companyId] });
    },
  });

  const remove = useMutation({
    mutationFn: (memberId: string) => rolesApi.remove(companyId, memberId),
    onSuccess: () => {
      toast.success('Role removed');
      qc.invalidateQueries({ queryKey: ['roles', companyId] });
    },
  });

  function togglePerm(p: Permission) {
    setPermissions((cur) => (cur.includes(p) ? cur.filter((x) => x !== p) : [...cur, p]));
  }

  return (
    <section>
      <Link to={`/companies/${companyId}`} className="btn ghost" style={{ marginBottom: '1rem' }}>
        ← Company
      </Link>
      <h1 className="page-title">Roles &amp; permissions</h1>

      {list.isLoading && <p>Loading…</p>}
      {list.data && (
        <table className="table" style={{ marginBottom: '1.5rem' }}>
          <thead>
            <tr>
              <th>Member</th>
              <th>Role</th>
              <th>Permissions</th>
              <th aria-label="actions" />
            </tr>
          </thead>
          <tbody>
            {list.data.length === 0 && (
              <tr>
                <td colSpan={4} className="empty">No appointments yet.</td>
              </tr>
            )}
            {list.data.map((r) => (
              <tr key={r.memberId}>
                <td>
                  {r.username ?? '—'}<br />
                  <code style={{ fontSize: '0.75rem' }}>{r.memberId}</code>
                </td>
                <td><code>{r.roleType}</code></td>
                <td>{r.permissions.join(', ') || '—'}</td>
                <td>
                  <button
                    type="button"
                    className="btn ghost"
                    onClick={() => remove.mutate(r.memberId)}
                  >
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2 style={{ fontSize: '1rem' }}>Appoint someone</h2>
      <form
        className="form-stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (!targetUsername) return;
          appoint.mutate();
        }}
      >
        <label>
          Username
          <input
            value={targetUsername}
            onChange={(e) => setTarget(e.target.value)}
            placeholder="username"
            required
          />
        </label>
        <label>
          Role
          <select
            value={roleType}
            onChange={(e) => setRoleType(e.target.value as RoleType)}
            style={{
              background: '#0d1117',
              color: '#e6edf3',
              border: '1px solid #30363d',
              borderRadius: 4,
              padding: '0.45rem 0.6rem',
              font: 'inherit',
            }}
          >
            <option value="MANAGER">Manager</option>
            <option value="OWNER">Owner</option>
          </select>
        </label>
        <fieldset style={{ border: '1px solid #30363d', borderRadius: 6, padding: '0.5rem 0.75rem' }}>
          <legend style={{ padding: '0 0.4rem', fontSize: '0.8rem', color: '#8b949e' }}>
            Permissions
          </legend>
          {ALL_PERMISSIONS.map((p) => (
            <label key={p} style={{ display: 'inline-flex', alignItems: 'center', gap: '0.3rem', marginRight: '1rem' }}>
              <input
                type="checkbox"
                checked={permissions.includes(p)}
                onChange={() => togglePerm(p)}
              />
              <code style={{ fontSize: '0.8rem' }}>{p}</code>
            </label>
          ))}
        </fieldset>
        <button type="submit" className="btn success" disabled={appoint.isPending}>
          {appoint.isPending ? 'Appointing…' : 'Appoint'}
        </button>
      </form>
    </section>
  );
}
