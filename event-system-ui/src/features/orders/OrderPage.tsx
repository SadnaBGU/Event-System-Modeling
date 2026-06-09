import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { ordersApi } from '../../api/endpoints/orders';
import { eventsApi } from '../../api/endpoints/events';
import { formatDateTime, formatMoney } from '../../lib/format';
import { InteractiveSeatMap } from '../../components/InteractiveSeatMap';
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

  const refetchOrder = () => qc.invalidateQueries({ queryKey: ['order', orderId] });

  const addItem = useMutation({
    mutationFn: (item: { zoneId: string; seatId: string }) => ordersApi.addItem(orderId, item),
    onSuccess: () => {
      toast.success('Added to cart');
      setSeatId('');
      refetchOrder();
    },
  });

  const removeItem = useMutation({
    mutationFn: (item: { zoneId: string; seatId: string }) => ordersApi.removeItem(orderId, item),
    onSuccess: () => {
      refetchOrder();
    },
  });

  const checkout = useMutation({
    mutationFn: () =>
      ordersApi.checkout({
        orderId,
        paymentToken: payment,
        discountCode: discount || undefined,
      }),
    onSuccess: () => {
      toast.success('Checkout submitted');
      qc.invalidateQueries({ queryKey: ['history'] });
      // Backend processes checkout asynchronously; bounce to receipts so the user can poll.
      navigate('/history');
    },
  });

  const subtotal = useMemo(() => {
    if (!orderQ.data) return { amount: 0, currency: 'USD' };
    const items = orderQ.data.items;
    const total = items.reduce((sum, i) => sum + i.unitPrice.amount * (i.quantity || 1), 0);
    const currency = items[0]?.unitPrice.currency ?? 'USD';
    return { amount: total, currency };
  }, [orderQ.data]);

  if (orderQ.isLoading) return <p>Loading…</p>;
  if (orderQ.isError || !orderQ.data) return <p className="empty">Order not found.</p>;

  const order = orderQ.data;
  const event = eventQ.data;
  const firstDate = event?.dates[0];

  return (
    <section>
      <h1 className="page-title">Your cart</h1>
      {event && (
        <p className="meta">
          For <strong>{event.eventName}</strong>{firstDate ? ` · ${formatDateTime(firstDate)}` : ''}
        </p>
      )}
      <p className="meta">Cart expires {formatDateTime(order.reservationExpiry)}</p>

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
                <tr key={`${i.zoneId}-${i.seatId}`}>
                  <td>{zone?.zoneName ?? i.zoneId}</td>
                  <td>{i.seatId}</td>
                  <td>{formatMoney(i.unitPrice.amount, i.unitPrice.currency)}</td>
                  <td>
                    <button
                      type="button"
                      className="btn ghost"
                      onClick={() => removeItem.mutate({ zoneId: i.zoneId, seatId: i.seatId })}
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
        <span>{formatMoney(subtotal.amount, subtotal.currency)}</span>
      </div>

      <h2 style={{ fontSize: '1rem', marginTop: '1.5rem' }}>Add seat</h2>
      <label>
        Select zone
          <select
            value={zoneId}
            onChange={(e) => setZoneId(e.target.value)}
            style={{ marginLeft: '10px', background: '#0d1117', color: '#e6edf3', padding: '0.45rem', borderRadius: '4px' }}
          >
            <option value="">Pick a zone…</option>
            {event?.zones.map((z) => (
              <option key={z.zoneId} value={z.zoneId}>
                {z.zoneName} ({formatMoney(z.price, z.currency)})
              </option>
            ))}
          </select>
      </label>

      {zoneId && event && (
         <InteractiveSeatMap 
            zoneId={zoneId}
            zoneName={event.zones.find(z => z.zoneId === zoneId)?.zoneName || ''}
            price={event.zones.find(z => z.zoneId === zoneId)?.price || 0}
            currency={event.zones.find(z => z.zoneId === zoneId)?.currency || 'USD'}
            capacity={event.zones.find(z => z.zoneId === zoneId)?.totalCapacity || 100}
            isLoading={addItem.isPending || removeItem.isPending}
            onSeatToggle={(seat, isSelected) => {
                if (isSelected) {
                    addItem.mutate({ zoneId, seatId: seat });
                } else {
                    removeItem.mutate({ zoneId, seatId: seat });
                }
            }}
         />
      )}

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
          {checkout.isPending ? 'Processing…' : `Pay ${formatMoney(subtotal.amount, subtotal.currency)}`}
        </button>
      </form>

      <p style={{ marginTop: '1.5rem' }}>
        <Link to="/events" className="btn ghost">Back to catalog</Link>
      </p>
    </section>
  );
}
