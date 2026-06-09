import { useState } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { eventsApi } from '../../api/endpoints/events';
import { lotteryApi } from '../../api/endpoints/lottery';
import { useAuthStore } from '../../auth/authStore';
import { formatDateTime, formatMoney } from '../../lib/format';
import { getGuestSessionId } from '../../utils/sessionHelper';
import '../../components/common.css';

export function EventDetailPage() {
  const { eventId = '' } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const session = useAuthStore((s) => s.session);
  const memberId = session?.memberId;

  const ev = useQuery({
    queryKey: ['event', eventId],
    queryFn: () => eventsApi.get(eventId),
    enabled: !!eventId,
  });

  const openOrder = useMutation({
    mutationFn: async () => {
      const isMember = !!memberId;
      const payload = {
        eventId: eventId,
        buyerType: isMember ? "MEMBER" : "GUEST",
        sessionId: isMember ? null : getGuestSessionId(),
        memberId: isMember ? memberId : null
      };

      const res = await fetch('/api/orders/active', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(isMember ? { 'Authorization': `Bearer ${session.token}` } : {})
        },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        if (res.status === 409) throw new Error('QUEUE');
        throw new Error('Failed to open cart');
      }
      return res.json();
    },
    onSuccess: (data) => {
      toast.success('Cart opened');
      navigate(`/orders/${data.orderId}`);
    },
    onError: (err: any) => {
      if (err.message === 'QUEUE') {
        toast.info('האירוע עמוס, מעביר אותך לתור הווירטואלי...');
        navigate(`/events/${eventId}/queue`);
      } else {
        toast.error(err.message);
      }
    }
  });

  const enterLottery = useMutation({
    mutationFn: () => lotteryApi.register(eventId),
    onSuccess: () => toast.success('Registered for the lottery'),
  });

  const publish = useMutation({
    mutationFn: () => eventsApi.publish(eventId),
    onSuccess: () => {
      toast.success('Event published');
      qc.invalidateQueries({ queryKey: ['event', eventId] });
      qc.invalidateQueries({ queryKey: ['events'] });
    },
  });

  const [zoneName, setZoneName] = useState('');
  const [zonePrice, setZonePrice] = useState<string>('');
  const [zoneCapacity, setZoneCapacity] = useState<string>('100');

  const addZone = useMutation({
    mutationFn: () =>
      eventsApi.addZone(eventId, {
        zoneName: zoneName.trim(),
        price: Number(zonePrice),
        currency: 'USD',
        capacity: Number(zoneCapacity),
      }),
    onSuccess: () => {
      toast.success('Zone added');
      setZoneName('');
      setZonePrice('');
      setZoneCapacity('100');
      qc.invalidateQueries({ queryKey: ['event', eventId] });
    },
  });

  if (ev.isLoading) return <p>Loading…</p>;
  if (ev.isError || !ev.data) return <p className="empty">Event not found.</p>;

  const e = ev.data;
  const firstDate = e.dates[0];
  const isDraft = e.status === 'DRAFT' || e.status === 'INITIALIZED';

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
        <div>Status: <code>{e.status}</code></div>
      </div>
      {e.description && <p>{e.description}</p>}

      <h2 style={{ fontSize: '1.1rem', marginTop: '1.5rem' }}>Zones</h2>
      {e.zones.length === 0 && <p className="empty">No zones yet.</p>}
      {e.zones.map((z) => (
        <div className="zone-row" key={z.zoneId}>
          <div className="zone-info">
            <strong>{z.zoneName}</strong> · {z.zoneType === 'SEATED' ? 'Seated' : 'Standing'}
            <div className="meta">{z.availableCount} of {z.totalCapacity} available</div>
          </div>
          <span className="price">{formatMoney(z.price, z.currency)}</span>
        </div>
      ))}

      {isDraft && (
        <details style={{ marginTop: '1rem' }}>
          <summary className="meta">Add a standing zone (organiser)</summary>
          <form
            className="form-stack"
            style={{ marginTop: '0.75rem' }}
            onSubmit={(ev2) => {
              ev2.preventDefault();
              if (!zoneName || !zonePrice || !zoneCapacity) return;
              addZone.mutate();
            }}
          >
            <label>
              Zone name
              <input value={zoneName} onChange={(ev2) => setZoneName(ev2.target.value)} placeholder="Floor" required />
            </label>
            <label>
              Price (USD)
              <input
                type="number"
                min={0}
                step="0.01"
                value={zonePrice}
                onChange={(ev2) => setZonePrice(ev2.target.value)}
                required
              />
            </label>
            <label>
              Capacity
              <input
                type="number"
                min={1}
                value={zoneCapacity}
                onChange={(ev2) => setZoneCapacity(ev2.target.value)}
                required
              />
            </label>
            <button type="submit" className="btn" disabled={addZone.isPending}>
              {addZone.isPending ? 'Adding…' : 'Add zone'}
            </button>
          </form>
        </details>
      )}

      <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem', flexWrap: 'wrap' }}>
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
        {isDraft && (
          <button
            type="button"
            className="btn"
            onClick={() => publish.mutate()}
            disabled={publish.isPending || e.zones.length === 0}
            title={e.zones.length === 0 ? 'Add at least one zone before publishing' : 'Publish so this event appears in the public catalog'}
          >
            {publish.isPending ? 'Publishing…' : 'Publish'}
          </button>
        )}
      </div>
    </section>
  );
}
