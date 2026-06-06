import { Link, useParams, useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';
import { eventsApi } from '../../api/endpoints/events';
import { ordersApi } from '../../api/endpoints/orders';
import { lotteryApi } from '../../api/endpoints/lottery';
import { useAuthStore } from '../../auth/authStore';
import { formatDateTime, formatMoney } from '../../lib/format';
import '../../components/common.css';

export function EventDetailPage() {
  const { eventId = '' } = useParams();
  const navigate = useNavigate();
  const memberId = useAuthStore((s) => s.session?.memberId);
  const ev = useQuery({
    queryKey: ['event', eventId],
    queryFn: () => eventsApi.get(eventId),
    enabled: !!eventId,
  });

  const openOrder = useMutation({
    mutationFn: () => {
      if (!memberId) throw new Error('Sign in to start an order');
      return ordersApi.openOrCreate(eventId, memberId);
    },
    onSuccess: ({ orderId }) => {
      toast.success('Cart opened');
      navigate(`/orders/${orderId}`);
    },
  });

  const enterLottery = useMutation({
    mutationFn: () => lotteryApi.register(eventId),
    onSuccess: () => toast.success('Registered for the lottery'),
  });

  if (ev.isLoading) return <p>Loading…</p>;
  if (ev.isError || !ev.data) return <p className="empty">Event not found.</p>;

  const e = ev.data;
  const firstDate = e.dates[0];
  return (
    <section>
      <Link to="/events" className="btn ghost" style={{ marginBottom: '1rem' }}>← Catalog</Link>
      <h1 className="page-title">{e.eventName}</h1>
      <div className="meta" style={{ marginBottom: '1rem' }}>
        {e.artist && <div><strong>{e.artist}</strong></div>}
        <div>
          {firstDate && formatDateTime(firstDate)}
          {e.location && ` · ${e.location}`}
        </div>
      </div>
      {e.description && <p>{e.description}</p>}

      <h2 style={{ fontSize: '1.1rem', marginTop: '1.5rem' }}>Zones</h2>
      {e.zones.map((z) => (
        <div className="zone-row" key={z.zoneId}>
          <div className="zone-info">
            <strong>{z.zoneName}</strong> · {z.zoneType === 'SEATED' ? 'Seated' : 'Standing'}
            <div className="meta">{z.availableCount} of {z.totalCapacity} available</div>
          </div>
          <span className="price">{formatMoney(z.price, z.currency)}</span>
        </div>
      ))}

      <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem' }}>
        <button
          type="button"
          className="btn success"
          onClick={() => openOrder.mutate()}
          disabled={openOrder.isPending}
        >
          {openOrder.isPending ? 'Opening…' : 'Start order'}
        </button>
        <Link to={`/events/${eventId}/queue`} className="btn ghost">Virtual queue</Link>
        <button
          type="button"
          className="btn ghost"
          onClick={() => enterLottery.mutate()}
          disabled={enterLottery.isPending}
        >
          {enterLottery.isPending ? 'Registering…' : 'Enter lottery'}
        </button>
      </div>
    </section>
  );
}
