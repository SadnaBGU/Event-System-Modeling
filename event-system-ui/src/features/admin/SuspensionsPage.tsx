import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { adminApi } from '../../api/endpoints/admin';
import { formatDateTime } from '../../lib/format';
import '../../components/common.css';

// Backend SuspensionDto.duration is "PERMANENT" or an ISO-8601 duration like "PT24H" / "P1D".
function formatDuration(d: string | null | undefined): string {
  if (!d || d === 'PERMANENT') return 'Permanent';
  // Quick ISO-8601 readability pass.
  const match = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?)?$/.exec(d);
  if (!match) return d;
  const [, dd, hh, mm] = match;
  const parts: string[] = [];
  if (dd) parts.push(`${dd}d`);
  if (hh) parts.push(`${hh}h`);
  if (mm) parts.push(`${mm}m`);
  return parts.length > 0 ? parts.join(' ') : d;
}

export function SuspensionsPage() {
  const qc = useQueryClient();
  const list = useQuery({
    queryKey: ['admin', 'suspensions'],
    queryFn: () => adminApi.listSuspensions(),
  });

  const [targetType, setTargetType] = useState<'memberId' | 'username'>('username');
  const [targetValue, setTarget] = useState('');
  const [duration, setDuration] = useState<string>('1'); // empty string => permanent
  const [reason, setReason] = useState('');

  const suspend = useMutation({
    mutationFn: () =>
      adminApi.suspend(
        targetType === 'memberId'
          ? { memberId: targetValue.trim() }
          : { username: targetValue.trim() },
        {
          durationDays: duration === '' ? null : Number(duration),
          reason: reason || undefined,
        },
      ),
    onSuccess: () => {
      toast.success('The member was suspended successfully.');
      setTarget('');
      setReason('');
      qc.invalidateQueries({ queryKey: ['admin', 'suspensions'] });
    },
  });

  const unsuspend = useMutation({
    mutationFn: (mid: string) => adminApi.unsuspend(mid),
    onSuccess: () => {
      toast.success('The suspension was lifted successfully.');
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
                  {s.username ?? '—'}<br />
                  <code style={{ fontSize: '0.75rem' }}>{s.memberId}</code>
                </td>
                <td>{formatDateTime(s.suspendedAt)}</td>
                <td>{formatDuration(s.duration)}</td>
                <td>{s.endsAt ? formatDateTime(s.endsAt) : '—'}</td>
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
          if (!targetValue.trim()) return;
          suspend.mutate();
        }}
      >
        <label>
          Find member by
          <select value={targetType} onChange={(e) => setTargetType(e.target.value as 'memberId' | 'username')}>
            <option value="username">Username</option>
            <option value="memberId">Member ID</option>
          </select>
        </label>
        <label>
          {targetType === 'memberId' ? 'Member ID' : 'Username'}
          <input
            value={targetValue}
            onChange={(e) => setTarget(e.target.value)}
            placeholder={targetType === 'memberId' ? 'MEM-XXXXX' : 'e.g. john_doe'}
            required
          />
        </label>
        <label>
          Duration (days — leave blank for permanent)
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
