import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { adminApi } from '../../api/endpoints/admin';
import { friendlyError } from '../../lib/errors';
import '../../components/common.css';

/**
 * Banning a member is a permanent suspension (duration = null): the account is
 * blocked from logging in / acting, which automatically nullifies their roles in
 * practice. This is the admin "ban/remove" action (UC22).
 */
export function BanMemberPage() {
  const [targetType, setTargetType] = useState<'memberId' | 'username'>('username');
  const [targetValue, setTargetValue] = useState('');
  const [reason, setReason] = useState('');
  const [confirmed, setConfirmed] = useState(false);

  const ban = useMutation({
    mutationFn: () =>
      adminApi.suspend(
        targetType === 'memberId'
          ? { memberId: targetValue.trim() }
          : { username: targetValue.trim() },
        { durationDays: null, reason: reason || 'Banned by admin' },
      ),
    onSuccess: () => {
      toast.success('The member was permanently suspended.');
      setTargetValue('');
      setReason('');
      setConfirmed(false);
    },
    onError: (err) => toast.error(friendlyError(err, "Couldn't ban this member.")),
  });

  return (
    <section>
      <Link to="/admin" className="btn ghost" style={{ marginBottom: '1rem' }}>← Admin</Link>
      <h1 className="page-title">Ban a member</h1>
      <p className="meta" style={{ marginBottom: '1rem' }}>
        A ban is a <strong>permanent suspension</strong> — the member can no longer sign in or act
        on the platform. To lift it later, remove the suspension on the{' '}
        <Link to="/admin/suspensions">suspensions</Link> page.
      </p>

      <form
        className="form-stack"
        style={{ maxWidth: '420px' }}
        onSubmit={(e) => {
          e.preventDefault();
          if (!targetValue.trim() || !confirmed) return;
          ban.mutate();
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
            onChange={(e) => setTargetValue(e.target.value)}
            placeholder={targetType === 'memberId' ? 'MEM-XXXXX' : 'e.g. john_doe'}
            required
          />
        </label>
        <label>
          Reason
          <input
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="e.g. Scalping / bot activity"
          />
        </label>
        <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <input type="checkbox" checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)} />
          I understand this permanently blocks the account.
        </label>
        <button type="submit" className="btn danger" disabled={ban.isPending || !confirmed}>
          {ban.isPending ? 'Banning…' : 'Ban member'}
        </button>
      </form>
    </section>
  );
}
