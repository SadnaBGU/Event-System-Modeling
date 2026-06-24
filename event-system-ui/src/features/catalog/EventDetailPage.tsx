import { useState } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { eventsApi } from '../../api/endpoints/events';
import { lotteryApi } from '../../api/endpoints/lottery';
import { useAuthStore } from '../../auth/authStore';
import { useCompanyPermissions } from '../../auth/useCompanyPermissions';
import { friendlyError } from '../../lib/errors';
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

  // What the current member may do inside the company that owns this event.
  // Guests / plain members get canManage = false, so organiser UI stays hidden.
  const perms = useCompanyPermissions(ev.data?.companyId);

  const lotteryQ = useQuery({
    queryKey: ['lottery', eventId],
    queryFn: () => lotteryApi.status(eventId),
    enabled: !!eventId,
  });

  const winnersQ = useQuery({
    queryKey: ['lottery-winners', eventId],
    queryFn: () => lotteryApi.winners(eventId),
    enabled: !!eventId && perms.canManage && lotteryQ.data?.status === 'DRAWN',
  });

  const openOrder = useMutation({
    mutationFn: async () => {
      const isMember = !!memberId;
      const payload = {
        eventId: eventId,
        buyerType: isMember ? 'MEMBER' : 'GUEST',
        sessionId: isMember ? null : getGuestSessionId(),
        memberId: isMember ? memberId : null,
      };

      const res = await fetch('/api/orders/active', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(isMember ? { Authorization: `Bearer ${session.token}` } : {}),
        },
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        if (res.status === 409) throw new Error('QUEUE');
        throw new Error('Failed to open cart');
      }
      return res.json();
    },
    onSuccess: (data) => {
      navigate(`/orders/${data.orderId}`);
    },
    onError: (err: Error) => {
      if (err.message === 'QUEUE') {
        toast.info('This event is busy — sending you to the virtual queue.');
        navigate(`/events/${eventId}/queue`);
      } else {
        toast.error("Couldn't open your cart. Please try again.");
      }
    },
  });

  const enterLottery = useMutation({
    mutationFn: () => lotteryApi.register(eventId),
    onSuccess: () => toast.success("You're entered in the lottery."),
    onError: (err) => toast.error(friendlyError(err, "Couldn't register for the lottery.")),
  });

  const createLottery = useMutation({
    mutationFn: () => lotteryApi.open(eventId),
    onSuccess: () => {
      toast.success('Lottery created.');
      qc.invalidateQueries({ queryKey: ['lottery', eventId] });
    },
    onError: (err) => toast.error(friendlyError(err, "Couldn't create the lottery.")),
  });

  const [winnerCount, setWinnerCount] = useState<string>('1');

  const drawLottery = useMutation({
    mutationFn: () => lotteryApi.draw(eventId, Number(winnerCount)),
    onSuccess: (res) => {
      toast.success(`Lottery drawn — ${res.winners} winner${res.winners === 1 ? '' : 's'} selected.`);
      qc.invalidateQueries({ queryKey: ['lottery', eventId] });
    },
    onError: (err) => toast.error(friendlyError(err, "Couldn't draw the lottery.")),
  });

  const publish = useMutation({
    mutationFn: () => eventsApi.publish(eventId),
    onSuccess: () => {
      toast.success('Event published.');
      qc.invalidateQueries({ queryKey: ['event', eventId] });
      qc.invalidateQueries({ queryKey: ['events'] });
    },
    onError: (err) => toast.error(friendlyError(err, "Couldn't publish the event.")),
  });

  const [zoneName, setZoneName] = useState('');
  const [zonePrice, setZonePrice] = useState<string>('');
  const [zoneType, setZoneType] = useState<'STANDING' | 'SEATED'>('STANDING');
  const [zoneCapacity, setZoneCapacity] = useState<string>('100');
  const [rows, setRows] = useState<string>('5');
  const [seatsPerRow, setSeatsPerRow] = useState<string>('10');

  const addZone = useMutation({
    mutationFn: () =>
      eventsApi.addZone(eventId, {
        zoneName: zoneName.trim(),
        price: Number(zonePrice),
        currency: 'USD',
        zoneType,
        capacity: zoneType === 'SEATED' ? Number(rows) * Number(seatsPerRow) : Number(zoneCapacity),
        rows: zoneType === 'SEATED' ? Number(rows) : undefined,
        seatsPerRow: zoneType === 'SEATED' ? Number(seatsPerRow) : undefined,
      }),
    onSuccess: () => {
      toast.success('Zone added.');
      setZoneName('');
      setZonePrice('');
      setZoneCapacity('100');
      qc.invalidateQueries({ queryKey: ['event', eventId] });
    },
    onError: (err) => toast.error(friendlyError(err, "Couldn't add the zone.")),
  });

  if (ev.isLoading) return <p>Loading…</p>;
  if (ev.isError || !ev.data) return <p className="empty">Event not found.</p>;

  const e = ev.data;
  const firstDate = e.dates[0];
  const isDraft = e.status === 'DRAFT' || e.status === 'INITIALIZED';
  const canManage = perms.canManage;
  const canManageInventory = perms.can('EVENT_INVENTORY_MANAGEMENT');
  const canManagePolicies = perms.can('MODIFY_POLICIES');
  const lotteryExists = lotteryQ.data?.exists ?? false;
  const lotteryOpen = lotteryQ.data?.status === 'REGISTRATION_OPEN';
  const lotteryDrawn = lotteryQ.data?.status === 'DRAWN';

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

      {/* ── Organiser-only controls (hidden entirely from guests/plain members) ── */}
      {isDraft && canManageInventory && (
        <details style={{ marginTop: '1rem' }}>
          <summary className="meta">Add a zone (organiser)</summary>
          <form
            className="form-stack"
            style={{ marginTop: '0.75rem' }}
            onSubmit={(ev2) => {
              ev2.preventDefault();
              if (!zoneName || !zonePrice) return;
              addZone.mutate();
            }}
          >
            <label>
              Zone type
              <select
                value={zoneType}
                onChange={(ev2) => setZoneType(ev2.target.value as 'STANDING' | 'SEATED')}
              >
                <option value="STANDING">Standing (general admission)</option>
                <option value="SEATED">Seated (selectable seat map)</option>
              </select>
            </label>
            <label>
              Zone name
              <input value={zoneName} onChange={(ev2) => setZoneName(ev2.target.value)} placeholder="Floor / Section A" required />
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
            {zoneType === 'STANDING' ? (
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
            ) : (
              <>
                <label>
                  Rows
                  <input type="number" min={1} max={26} value={rows} onChange={(ev2) => setRows(ev2.target.value)} required />
                </label>
                <label>
                  Seats per row
                  <input type="number" min={1} value={seatsPerRow} onChange={(ev2) => setSeatsPerRow(ev2.target.value)} required />
                </label>
                <p className="meta">{Number(rows) * Number(seatsPerRow)} seats total</p>
              </>
            )}
            <button type="submit" className="btn" disabled={addZone.isPending}>
              {addZone.isPending ? 'Adding…' : 'Add zone'}
            </button>
          </form>
        </details>
      )}

      <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem', flexWrap: 'wrap' }}>
        {/* ── Buyer actions (everyone) ── */}
        <button
          type="button"
          className="btn success"
          onClick={() => openOrder.mutate()}
          disabled={openOrder.isPending}
        >
          {openOrder.isPending ? 'Opening…' : 'Start order'}
        </button>
        <Link to={`/events/${eventId}/queue`} className="btn ghost">Virtual queue</Link>

        {/* Participants can enter only when a lottery is open. */}
        {lotteryExists && lotteryOpen && (
          <button
            type="button"
            className="btn ghost"
            onClick={() => {
              if (!session) {
                toast.info('Please sign in to enter the lottery.');
                navigate('/login', { state: { from: `/events/${eventId}` } });
                return;
              }
              enterLottery.mutate();
            }}
            disabled={enterLottery.isPending}
          >
            {enterLottery.isPending ? 'Entering…' : '🎟️ Enter lottery'}
          </button>
        )}

        {/* ── Organiser actions (hidden from guests/plain members) ── */}
        {canManagePolicies && (
          <Link to={`/events/${eventId}/policies`} className="btn ghost">Edit policies</Link>
        )}
        {isDraft && canManageInventory && (
          <Link to={`/events/${eventId}/edit`} className="btn ghost">Edit event</Link>
        )}
        {!isDraft && canManageInventory && (
          <span className="meta" style={{ alignSelf: 'center' }}>
            Published events can’t be edited
          </span>
        )}
        {canManageInventory && !lotteryExists && (
          <button
            type="button"
            className="btn ghost"
            onClick={() => createLottery.mutate()}
            disabled={createLottery.isPending}
          >
            {createLottery.isPending ? 'Creating…' : '➕ Create lottery'}
          </button>
        )}
        {canManage && lotteryExists && (
          <span className="meta" style={{ alignSelf: 'center' }}>
            Lottery: {lotteryQ.data?.status?.toLowerCase().replace('_', ' ')}
          </span>
        )}
        {canManageInventory && lotteryExists && !lotteryDrawn && (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}>
            <input
              type="number"
              min={1}
              value={winnerCount}
              onChange={(e) => setWinnerCount(e.target.value)}
              style={{ width: '5rem' }}
              aria-label="Number of winners to draw"
              title="Number of winners to draw"
            />
            <button
              type="button"
              className="btn"
              onClick={() => drawLottery.mutate()}
              disabled={drawLottery.isPending || Number(winnerCount) < 1}
            >
              {drawLottery.isPending ? 'Drawing…' : '🎲 Draw winners'}
            </button>
          </span>
        )}
        {isDraft && canManageInventory && (
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

      {canManage && lotteryDrawn && (
        <div className="card" style={{ marginTop: '1.5rem' }}>
          <h2 style={{ fontSize: '1.05rem', marginTop: 0 }}>Lottery winners</h2>
          {winnersQ.isLoading && <p>Loading…</p>}
          {winnersQ.isError && <p className="empty">Could not load winners.</p>}
          {winnersQ.data && winnersQ.data.length === 0 && (
            <p className="empty">No winners were drawn (no one had registered).</p>
          )}
          {winnersQ.data && winnersQ.data.length > 0 && (
            <table className="table">
              <thead>
                <tr>
                  <th>Winner</th>
                  <th>Code expires</th>
                </tr>
              </thead>
              <tbody>
                {winnersQ.data.map((w) => (
                  <tr key={w.memberId}>
                    <td>{w.username}</td>
                    <td>{formatDateTime(w.codeExpiry)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </section>
  );
}
