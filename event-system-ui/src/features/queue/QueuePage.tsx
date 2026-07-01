import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { queueApi } from '../../api/endpoints/queue';
import '../../components/common.css';

export function QueuePage() {
  const { eventId = '' } = useParams();
  const qc = useQueryClient();

  const status = useQuery({
    queryKey: ['queue', eventId],
    queryFn: () => queueApi.status(eventId),
    enabled: !!eventId,
    // Single fetch on mount; real-time updates arrive via WebSocket.
    // No polling: V2 forbids it.
    refetchOnMount: true,
    refetchInterval: false,
  });

  const leave = useMutation({
    mutationFn: () => queueApi.leave(eventId),
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
      {status.data && (
        <>
          {status.data.isAdmitted ? (
            <div className="card">
              <h3>You're in</h3>
              <p className="meta">Position {status.data.position ?? 0}. You may now buy tickets.</p>
              <div className="actions">
                <Link to={`/events/${eventId}`} className="btn">Continue to event</Link>
                <button type="button" className="btn ghost" onClick={() => leave.mutate()}>
                  Leave queue
                </button>
              </div>
            </div>
          ) : (
            <div className="card">
              <h3>Waiting room status</h3>
              <p className="meta">Queue entry is automatic under high load. Start an order from the event page; if load is high, you'll be placed here automatically.</p>
            </div>
          )}
        </>
      )}
    </section>
  );
}
