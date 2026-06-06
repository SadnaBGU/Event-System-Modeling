import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { ordersApi } from '../../api/endpoints/orders';
import { eventsApi } from '../../api/endpoints/events';
import { formatDateTime, formatMoney } from '../../lib/format';
import '../../components/common.css';

export function OrderPage() {
  const { orderId = '' } = useParams();
  const qc = useQueryClient();
  const navigate = useNavigate();

  const orderQ = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => ordersApi.get(orderId),
    enabled: !!orderId,
  });

  const eventQ = useQuery({
    queryKey: ['event', orderQ.data?.eventId],
    queryFn: () => eventsApi.get(orderQ.data!.eventId),
    enabled: !!orderQ.data?.eventId,
  });

  const [zoneId, setZoneId] = useState('');
  const [seatId, setSeatId] = useState('');
  const [discount, setDiscount] = useState('');
  const [payment, setPayment] = useState('tok_visa_mock');

  const addItem = useMutation({
    mutationFn: () => ordersApi.addItem(orderId, { zoneId, seatId }),
    onSuccess: (updated) => {
      toast.success('Added to cart');
      qc.setQueryData(['order', orderId], updated);
      setSeatId('');
    },
  });

  const removeItem = useMutation({
    mutationFn: (sid: string) => ordersApi.removeItem(orderId, sid),
    onSuccess: (updated) => {
      qc.setQueryData(['order', orderId], updated);
    },
  });

  const checkout = useMutation({
    mutationFn: () =>
      ordersApi.checkout(orderId, {
        paymentToken: payment,
        discountCode: discount || undefined,
      }),
    onSuccess: ({ recordId }) => {
      toast.success('Purchase confirmed');
      qc.invalidateQueries({ queryKey: ['history'] });
      navigate(`/history/${recordId}`);
    },
  });

  if (orderQ.isLoading) return <p>Loading…</p>;
  if (orderQ.isError || !orderQ.data) return <p className="empty">Order not found.</p>;

  const order = orderQ.data;
  const event = eventQ.data;

  return (
    <section>
      <h1 className="page-title">Your cart</h1>
      {event && (
        <p className="meta">
          For <strong>{event.name}</strong> · {formatDateTime(event.dateTime)}
        </p>
      )}
      <p className="meta">Cart expires {formatDateTime(order.expiresAt)}</p>

      <h2 style={{ fontSize: '1rem', marginTop: '1rem' }}>Items</h2>
      {order.items.length === 0 ? (
        <p className="empty">Your cart is empty. Add a seat below.</p>
      ) : (
        <table className="table" style={{ marginBottom: '1rem' }}>
          <thead>
            <tr>
              <th>Zone</th>
              <th>Seat</th>
              <th>Price</th>
              <th aria-label="actions" />
            </tr>
          </thead>
          <tbody>
            {order.items.map((i) => {
              const zone = event?.zones.find((z) => z.zoneId === i.zoneId);
              return (
                <tr key={i.seatId}>
                  <td>{zone?.name ?? i.zoneId}</td>
                  <td>{i.seatLabel ?? i.seatId}</td>
                  <td>{formatMoney(i.unitPrice)}</td>
                  <td>
                    <button
                      type="button"
                      className="btn ghost"
                      onClick={() => removeItem.mutate(i.seatId)}
                      disabled={removeItem.isPending}
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      <div className="totals">
        <span>Subtotal</span>
        <span>{formatMoney(order.totalBeforeDiscount)}</span>
      </div>
      {order.totalAfterDiscount !== order.totalBeforeDiscount && (
        <div className="totals" style={{ borderTop: 'none' }}>
          <span>After discount</span>
          <span>{formatMoney(order.totalAfterDiscount)}</span>
        </div>
      )}

      <h2 style={{ fontSize: '1rem', marginTop: '1.5rem' }}>Add seat</h2>
      <form
        className="form-stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (!zoneId || !seatId) return;
          addItem.mutate();
        }}
      >
        <label>
          Zone
          <select
            value={zoneId}
            onChange={(e) => setZoneId(e.target.value)}
            required
            style={{
              background: '#0d1117',
              color: '#e6edf3',
              border: '1px solid #30363d',
              borderRadius: 4,
              padding: '0.45rem 0.6rem',
              font: 'inherit',
            }}
          >
            <option value="">Pick a zone…</option>
            {event?.zones.map((z) => (
              <option key={z.zoneId} value={z.zoneId}>
                {z.name} ({formatMoney(z.basePrice)})
              </option>
            ))}
          </select>
        </label>
        <label>
          Seat ID
          <input
            value={seatId}
            onChange={(e) => setSeatId(e.target.value)}
            required
            placeholder="e.g. A-12"
          />
        </label>
        <button className="btn" type="submit" disabled={addItem.isPending}>
          {addItem.isPending ? 'Adding…' : 'Add to cart'}
        </button>
      </form>

      <h2 style={{ fontSize: '1rem', marginTop: '1.5rem' }}>Checkout</h2>
      <form
        className="form-stack"
        onSubmit={(e) => {
          e.preventDefault();
          checkout.mutate();
        }}
      >
        <label>
          Payment token
          <input value={payment} onChange={(e) => setPayment(e.target.value)} required />
        </label>
        <label>
          Discount code (optional)
          <input
            value={discount}
            onChange={(e) => setDiscount(e.target.value)}
            placeholder="PROMO10"
          />
        </label>
        <button
          className="btn success"
          type="submit"
          disabled={checkout.isPending || order.items.length === 0}
        >
          {checkout.isPending ? 'Processing…' : `Pay ${formatMoney(order.totalAfterDiscount)}`}
        </button>
      </form>

      <p style={{ marginTop: '1.5rem' }}>
        <Link to="/events" className="btn ghost">Back to catalog</Link>
      </p>
    </section>
  );
}
