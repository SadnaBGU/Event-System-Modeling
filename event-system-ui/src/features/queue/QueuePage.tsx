import { useEffect, useRef } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { queueApi } from '../../api/endpoints/queue';
import { getGuestSessionId } from '../../utils/sessionHelper';
import { useAuthStore } from '../../auth/authStore';
import '../../components/common.css';

export function QueuePage() {
  const { eventId = '' } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const session = useAuthStore((s) => s.session);
  const hasStartedOrderRef = useRef(false);

  const isMember = !!session?.memberId;
  const guestSessionId = isMember ? undefined : getGuestSessionId();

  const status = useQuery({
    queryKey: ['queue', eventId, isMember ? session?.memberId : guestSessionId],
    queryFn: () => queueApi.status(eventId, guestSessionId),
    enabled: !!eventId,
    refetchOnMount: true,
    retry: 1,
    // Keep status fresh for both member and guest users so we can auto-advance quickly.
    refetchInterval: (query) => {
      if (query.state.error) return 3000;
      console.log('Queue status refetch interval', query.state.data?.isAdmitted);
      return query.state.data?.isAdmitted ? false : 1500;
    },
  });

  const openOrder = useMutation({
    mutationFn: async () => {
      const payload = {
        eventId,
        buyerType: isMember ? 'MEMBER' : 'GUEST',
        sessionId: isMember ? null : guestSessionId,
        memberId: isMember ? session?.memberId : null,
      };

      const res = await fetch('/api/orders/active', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(isMember && session?.token ? { Authorization: `Bearer ${session.token}` } : {}),
        },
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        throw new Error('Could not open order from queue admission');
      }

      return res.json() as Promise<{ orderId: string }>;
    },
    onSuccess: (data) => {
      navigate(`/orders/${data.orderId}`);
    },
    onError: () => {
      hasStartedOrderRef.current = false;
      toast.error('Your turn arrived, but opening the order failed. Please try again.');
    },
  });

  useEffect(() => {
    if (!status.data?.isAdmitted) return;
    if (hasStartedOrderRef.current) return;
    hasStartedOrderRef.current = true;
    toast.success('It is your turn. Opening your order now.');
    openOrder.mutate();
  }, [status.data?.isAdmitted]);

  const leave = useMutation({
    mutationFn: () => queueApi.leave(eventId, guestSessionId),
    onSuccess: () => {
      toast.success('You left the virtual queue.');
      qc.invalidateQueries({ queryKey: ['queue', eventId] });
    },
  });

  return (
    <section>
      <Link to={`/events/${eventId}`} className="btn ghost" style={{ marginBottom: '1rem' }}>← Event</Link>
      <h1 className="page-title">Virtual queue</h1>

      {status.isLoading && <p>Checking your place…</p>}
      {status.isError && (
        <div className="card">
          <h3>Could not load queue status</h3>
          <p className="meta">Please refresh this page in a few seconds. If this continues, the server queue-status endpoint is failing.</p>
        </div>
      )}
      {status.data && (
        <>
          {status.data.isAdmitted ? (
            <div className="card">
              <h3>You're in</h3>
              <p className="meta">Your turn has arrived. Opening your order now…</p>
              <div className="actions">
                <button type="button" className="btn" onClick={() => openOrder.mutate()} disabled={openOrder.isPending}>
                  {openOrder.isPending ? 'Opening order…' : 'Open order now'}
                </button>
                <button type="button" className="btn ghost" onClick={() => leave.mutate()}>
                  Leave queue
                </button>
              </div>
            </div>
          ) : (
            <div className="card">
              <h3>Waiting room status</h3>
              <p className="meta">
                {typeof status.data.position === 'number' && status.data.position > 0
                  ? `${Math.max(0, status.data.position - 1)} people are ahead of you.`
                  : 'You are in queue. Waiting for your turn.'}
              </p>
            </div>
          )}
        </>
      )}
    </section>
  );
}
