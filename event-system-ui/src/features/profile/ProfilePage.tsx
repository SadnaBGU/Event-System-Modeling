import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { authApi } from '../../api/endpoints/auth';
import { useAuthStore } from '../../auth/authStore';
import { friendlyError } from '../../lib/errors';
import '../../components/common.css';

export function ProfilePage() {
  const session = useAuthStore((s) => s.session);
  const memberId = session?.memberId ?? '';
  const qc = useQueryClient();

  const me = useQuery({
    queryKey: ['member', memberId],
    queryFn: () => authApi.getMember(memberId),
    enabled: !!memberId,
  });

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [dateOfBirth, setDob] = useState('');

  useEffect(() => {
    if (!me.data) return;
    setFirstName(me.data.firstName);
    setLastName(me.data.lastName);
    setEmail(me.data.email);
    setDob(me.data.dateOfBirth);
  }, [me.data]);

  const save = useMutation({
    mutationFn: () =>
      authApi.updateMemberDetails(memberId, { firstName, lastName, email, dateOfBirth }),
    onSuccess: () => {
      toast.success('Your profile was updated successfully.');
      qc.invalidateQueries({ queryKey: ['member', memberId] });
    },
    onError: (err) => toast.error(friendlyError(err, "Couldn't update your profile.")),
  });

  if (me.isLoading) return <p>Loading…</p>;
  if (me.isError || !me.data) return <p className="empty">Couldn't load your profile.</p>;

  return (
    <section>
      <Link to="/" className="btn ghost" style={{ marginBottom: '1rem' }}>← Home</Link>
      <h1 className="page-title">My profile</h1>
      <p className="meta">
        Username <strong>{me.data.username}</strong> (cannot be changed) ·
        Status <code>{me.data.status}</code>
      </p>
      <p className="meta">
        Member ID <code>{me.data.memberId}</code> ·
      </p>

      <form
        className="form-stack"
        style={{ maxWidth: '420px' }}
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <label>
          First name
          <input value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
        </label>
        <label>
          Last name
          <input value={lastName} onChange={(e) => setLastName(e.target.value)} required />
        </label>
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          Date of birth
          <input type="date" value={dateOfBirth} onChange={(e) => setDob(e.target.value)} required />
        </label>
        <button type="submit" className="btn success" disabled={save.isPending}>
          {save.isPending ? 'Saving…' : 'Save changes'}
        </button>
      </form>
    </section>
  );
}
