import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { adminApi } from '../../api/endpoints/admin';
import { formatDateTime } from '../../lib/format';
import '../../components/common.css';

export function SuspensionsPage() {
  const qc = useQueryClient();
  const list = useQuery({
    queryKey: ['admin', 'suspensions'],
    queryFn: () => adminApi.listSuspensions(),
  });

  const [targetMemberId, setTarget] = useState('');
  const [duration, setDuration] = useState<string>('60'); // empty string => permanent
  const [reason, setReason] = useState('');

  const suspend = useMutation({
    mutationFn: () =>
      adminApi.suspend(targetMemberId, {
        durationMinutes: duration === '' ? null : Number(duration),
        reason: reason || undefined,
      }),
    onSuccess: () => {
      toast.success('Member suspended');
      setTarget('');
      setReason('');
      qc.invalidateQueries({ queryKey: ['admin', 'suspensions'] });
    },
  });

  const unsuspend = useMutation({
    mutationFn: (mid: string) => adminApi.unsuspend(mid),
    onSuccess: () => {
      toast.success('Suspension lifted');
      qc.invalidateQueries({ queryKey: ['admin', 'suspensions'] });
    },
  });

  return (
    <section>
      <Link to="/admin" className="btn ghost" style={{ marginBottom: '1rem' }}>← Admin</Link>
      <h1 className="page-title">Suspensions</h1>

      {list.isLoading && <p>Loading…</p>}
      {list.data && (
        <table className="table" style={{ marginBottom: '1.5rem' }}>
          <thead>
            <tr>
              <th>Member</th>
              <th>Suspended at</th>
              <th>Duration</th>
              <th>Ends at</th>
              <th>Reason</th>
              <th aria-label="actions" />
            </tr>
          </thead>
          <tbody>
            {list.data.length === 0 && (
              <tr><td colSpan={6} className="empty">No active suspensions.</td></tr>
            )}
            {list.data.map((s) => (
              <tr key={s.memberId}>
                <td>
                  {s.username}<br />
                  <code style={{ fontSize: '0.75rem' }}>{s.memberId}</code>
                </td>
                <td>{formatDateTime(s.suspendedAt)}</td>
                <td>{s.durationMinutes === null ? 'Permanent' : `${s.durationMinutes} min`}</td>
                <td>{s.endsAt ? formatDateTime(s.endsAt) : '—'}</td>
                <td>{s.reason ?? '—'}</td>
                <td>
                  <button
                    type="button"
                    className="btn ghost"
                    onClick={() => unsuspend.mutate(s.memberId)}
                  >
                    Unsuspend
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2 style={{ fontSize: '1rem' }}>Suspend a member</h2>
      <form
        className="form-stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (!targetMemberId) return;
          suspend.mutate();
        }}
      >
        <label>
          Member ID
          <input
            value={targetMemberId}
            onChange={(e) => setTarget(e.target.value)}
            placeholder="MEM-XXXXX"
            required
          />
        </label>
        <label>
          Duration (minutes — leave blank for permanent)
          <input
            type="number"
            min={1}
            value={duration}
            onChange={(e) => setDuration(e.target.value)}
          />
        </label>
        <label>
          Reason (optional)
          <input value={reason} onChange={(e) => setReason(e.target.value)} />
        </label>
        <button type="submit" className="btn danger" disabled={suspend.isPending}>
          {suspend.isPending ? 'Suspending…' : 'Suspend'}
        </button>
      </form>
    </section>
  );
}
