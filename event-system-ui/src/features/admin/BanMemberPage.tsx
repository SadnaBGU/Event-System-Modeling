import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { adminApi } from '../../api/endpoints/admin';
import '../../components/common.css';

export function BanMemberPage() {
  const [memberId, setMemberId] = useState('');
  const ban = useMutation({
    mutationFn: () => adminApi.banMember(memberId),
    onSuccess: () => {
      toast.success('Member banned');
      setMemberId('');
    },
  });

  return (
    <section>
      <Link to="/admin" className="btn ghost" style={{ marginBottom: '1rem' }}>← Admin</Link>
      <h1 className="page-title">Ban a member</h1>
      <p className="meta">
        Permanent removal. The member cannot be restored except by data migration.
      </p>
      <form
        className="form-stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (!memberId) return;
          if (!window.confirm(`Permanently ban ${memberId}?`)) return;
          ban.mutate();
        }}
      >
        <label>
          Member ID
          <input
            value={memberId}
            onChange={(e) => setMemberId(e.target.value)}
            placeholder="MEM-XXXXX"
            required
          />
        </label>
        <button type="submit" className="btn danger" disabled={ban.isPending}>
          {ban.isPending ? 'Banning…' : 'Permanently ban'}
        </button>
      </form>
    </section>
  );
}
