import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { ordersApi } from '../../api/endpoints/orders';
import { eventsApi } from '../../api/endpoints/events';
import { friendlyError, apiErrorCode} from '../../lib/errors';
import { formatDateTime, formatMoney } from '../../lib/format';
import { InteractiveSeatMap } from '../../components/InteractiveSeatMap';
import '../../components/common.css';

const TICKET_ISSUANCE_FAILURE = 'TICKET_ISSUANCE_FAILURE';

export function OrderPage() {

  const [cancelledCheckoutPopup, setCancelledCheckoutPopup] = useState<{
        message: string;
        eventId?: string;
      } | null>(null);

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
  const [quantity, setQuantity] = useState<number>(1);
  const [discount, setDiscount] = useState('');
  // WSEP expects the payment token to be JSON with card_number, month, year, holder, cvv, id.
  const [payment, setPayment] = useState(
    JSON.stringify({
      card_number: '4111111111111111',
      month: '12',
      year: '2030',
      holder: 'Test Buyer',
      cvv: '123',
      id: '123456789',
    }),
  );

  const refetchOrder = () => qc.invalidateQueries({ queryKey: ['order', orderId] });
  const refetchSeats = () => qc.invalidateQueries({ queryKey: ['zone-seats'] });

  const addItem = useMutation({
    mutationFn: (item: { zoneId: string; seatId?: string; quantity?: number }) => ordersApi.addItem(orderId, item),
    onSuccess: (_data, item) => {
      const n = item.quantity ?? 1;
      toast.success(n > 1 ? `${n} tickets added to cart` : 'Added to cart');
      refetchOrder();
      refetchSeats();
    },
    onError: (err) => {
      toast.error(friendlyError(err, "Couldn't add the ticket. It may already be taken."));
      refetchSeats();
    }
  });

  const removeItem = useMutation({
    mutationFn: (item: { zoneId: string; seatId?: string; quantity?: number }) => ordersApi.removeItem(orderId, item),
    onSuccess: () => {
      refetchOrder();
      refetchSeats();
    },
    onError: (err) => {
      toast.error(friendlyError(err, "Couldn't remove the ticket."));
    }
  });

  const checkout = useMutation({
    mutationFn: () =>
      ordersApi.checkout({
        orderId,
        paymentToken: payment,
        discountCode: discount || undefined,
      }),
    onSuccess: () => {
      toast.success('Purchase submitted — check your receipts.');
      qc.invalidateQueries({ queryKey: ['history'] });
      // Backend processes checkout asynchronously; bounce to receipts so the user can poll.
      navigate('/history');
    },
    onError: (err) => {
      if (apiErrorCode(err) === TICKET_ISSUANCE_FAILURE) {
        const eventId = orderQ.data?.eventId;

        qc.invalidateQueries({ queryKey: ['order', orderId] });
        qc.invalidateQueries({ queryKey: ['zone-seats'] });

        if (eventId) {
          qc.invalidateQueries({ queryKey: ['event', eventId] });
        }

        setCancelledCheckoutPopup({
          eventId,
          message: friendlyError(
            'Ticket Issuance Failed!',
            'Ticket Issuance Failure has occurred!\n a refund was requested for purchase'
          )
        });

        return;
      }
      else{
        toast.error(friendlyError(err, "Checkout couldn't be completed."));
      }
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

  const selectedZone = event?.zones.find((z) => z.zoneId === zoneId);
  const isSeated = selectedZone?.zoneType === 'SEATED';

  return (
    <section>
      {cancelledCheckoutPopup && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.65)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
          }}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="checkout-failed-title"
            style={{
              width: 'min(520px, calc(100vw - 2rem))',
              background: '#161b22',
              border: '1px solid #30363d',
              borderRadius: '12px',
              padding: '1.5rem',
              color: '#e6edf3',
              boxShadow: '0 20px 60px rgba(0, 0, 0, 0.45)',
            }}
          >
            <h2 id="checkout-failed-title" style={{ marginTop: 0 }}>
              Checkout failed!
            </h2>

           <p>
            <strong>Ticket issuance error has occurred.</strong>
          </p>

          <p>
            <strong>A refund was requested for this purchase.</strong>
          </p>

            <p className="meta">
              You will be returned to the event page, where you can start a new order.
            </p>

            <button
              className="btn"
              type="button"
              onClick={() => {
                if (cancelledCheckoutPopup.eventId) {
                  navigate(`/events/${cancelledCheckoutPopup.eventId}`, { replace: true });
                } else {
                  navigate('/events', { replace: true });
                }
              }}
            >
              Back to event
            </button>
          </div>
        </div>
      )}
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
                      onClick={() => removeItem.mutate({ zoneId: i.zoneId, seatId: i.seatId, quantity: 1 })}
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
            onChange={(e) => { 
              setZoneId(e.target.value);
              setQuantity(1);
            }}
            required
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

      {zoneId && selectedZone && (
        isSeated ? (
          <InteractiveSeatMap 
            zoneId={zoneId}
            zoneName={selectedZone.zoneName}
            price={selectedZone.price}
            currency={selectedZone.currency}
            capacity={selectedZone.totalCapacity}
            isLoading={addItem.isPending || removeItem.isPending}
            onSeatToggle={(seat, isSelected) => {
                if (isSelected) {
                    addItem.mutate({ zoneId, seatId: seat, quantity: 1 });
                } else {
                    removeItem.mutate({ zoneId, seatId: seat, quantity: 1 });
                }
            }}
         />
        ) : (
          <div className="form-stack" style={{ marginTop: '1.5rem', maxWidth: '320px' }}>
             <p className="meta">
               General admission — choose how many tickets to buy at once
               (available: {selectedZone.availableCount}).
             </p>
             <label>
               Tickets
               <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '0.35rem' }}>
                 <button
                   type="button"
                   className="btn ghost"
                   aria-label="Decrease quantity"
                   onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                   disabled={quantity <= 1}
                 >
                   −
                 </button>
                 <input
                   type="number"
                   min={1}
                   max={selectedZone.availableCount}
                   value={quantity}
                   onChange={(e) => {
                     const n = Number(e.target.value);
                     if (Number.isNaN(n)) return;
                     setQuantity(Math.min(Math.max(1, n), selectedZone.availableCount));
                   }}
                   style={{ width: '5rem', textAlign: 'center' }}
                 />
                 <button
                   type="button"
                   className="btn ghost"
                   aria-label="Increase quantity"
                   onClick={() => setQuantity((q) => Math.min(selectedZone.availableCount, q + 1))}
                   disabled={quantity >= selectedZone.availableCount}
                 >
                   +
                 </button>
               </div>
             </label>
             <p className="meta">
               Subtotal for selection: {formatMoney(selectedZone.price * quantity, selectedZone.currency)}
             </p>
             <button
               className="btn"
               type="button"
               onClick={() => addItem.mutate({ zoneId, quantity })}
               disabled={addItem.isPending || selectedZone.availableCount < 1}
             >
               {addItem.isPending ? 'Adding…' : `Add ${quantity} ${quantity > 1 ? 'tickets' : 'ticket'} to cart`}
             </button>
          </div>
        )
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
          disabled={checkout.isPending || order.items.length === 0 || !!cancelledCheckoutPopup}
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
