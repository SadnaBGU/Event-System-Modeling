import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { ordersApi } from '../../api/endpoints/orders';
import { eventsApi } from '../../api/endpoints/events';
import { friendlyError, apiErrorCode } from '../../lib/errors';
import { formatDateTime, formatMoney } from '../../lib/format';
import { InteractiveSeatMap } from '../../components/InteractiveSeatMap';
import '../../components/common.css';

const TICKET_ISSUANCE_FAILURE = 'TICKET_ISSUANCE_FAILURE';
const PURCHASE_POLICY_VIOLATION = 'PURCHASE_POLICY_VIOLATION';

function purchasePolicyReason(err: unknown): string {
  if (err && typeof err === 'object' && 'response' in err) {
    const data = (err as { response?: { data?: { policyReason?: unknown; message?: unknown } } }).response?.data;

    if (typeof data?.policyReason === 'string' && data.policyReason.trim()) {
      return data.policyReason;
    }

    if (typeof data?.message === 'string' && data.message.trim()) {
      return data.message;
    }
  }

  return 'Your order violates the purchase policy.';
}

export function OrderPage() {
  const [policyNotice, setPolicyNotice] = useState<string | null>(null);

  const [cancelledCheckoutPopup, setCancelledCheckoutPopup] = useState<{
    message: string;
    eventId?: string;
  } | null>(null);

  const { orderId = '' } = useParams();
  const qc = useQueryClient();
  const navigate = useNavigate();

  useEffect(() => {
    if (!policyNotice) return;

    const timer = window.setTimeout(() => {
      setPolicyNotice(null);
    }, 7000);

    return () => window.clearTimeout(timer);
  }, [policyNotice]);

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

  const seatLabelsQ = useQuery({
    queryKey: ['event-seat-labels', eventQ.data?.eventId, eventQ.data?.zones.map((z) => z.zoneId).join(',')],
    enabled: !!eventQ.data,
    queryFn: async () => {
      const seatedZones = (eventQ.data?.zones ?? []).filter((z) => z.zoneType === 'SEATED');
      const zonesWithSeats = await Promise.all(seatedZones.map((z) => eventsApi.zoneSeats(z.zoneId)));

      const labels = new Map<string, string>();
      for (const zone of zonesWithSeats) {
        for (const seat of zone.seats) {
          labels.set(seat.seatId, `Row ${seat.rowLabel}, Seat ${seat.seatNumber}`);
        }
      }
      return labels;
    },
  });

  const [zoneId, setZoneId] = useState('');
  const [quantity, setQuantity] = useState<number>(1);
  const [discount, setDiscount] = useState('');
  const [pricingPreview, setPricingPreview] = useState<{
    subtotal: number;
    discount: number;
    total: number;
    currency: string;
  } | null>(null);

  // WSEP expects the payment token to be JSON with card_number, month, year, holder, cvv, id.
  const [cardNumber, setCardNumber] = useState('4111111111111111');
  const [month, setMonth] = useState('12');
  const [year, setYear] = useState('2030');
  const [holder, setHolder] = useState('Test Buyer');
  const [cvv, setCvv] = useState('123');
  const [id, setId] = useState('123456789');

  const refetchOrder = () => qc.invalidateQueries({ queryKey: ['order', orderId] });
  const refetchSeats = () => qc.invalidateQueries({ queryKey: ['zone-seats'] });

  const addItem = useMutation({
    mutationFn: (item: { zoneId: string; seatId?: string; quantity?: number }) => ordersApi.addItem(orderId, item),
    onSuccess: (_data, item) => {
      const n = item.quantity ?? 1;
      toast.success(n > 1 ? `${n} tickets were added to your cart.` : 'The ticket was added to your cart.');
      setPricingPreview(null);
      setPolicyNotice(null);
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
      setPricingPreview(null);
      setPolicyNotice(null);
      refetchOrder();
      refetchSeats();
    },
    onError: (err) => {
      toast.error(friendlyError(err, "Couldn't remove the ticket."));
    }
  });

  const checkout = useMutation({
    mutationFn: () => {
      const paymentToken = JSON.stringify({
        card_number: cardNumber,
        month,
        year,
        holder,
        cvv,
        id,
      });

      return ordersApi.checkout({
        orderId,
        paymentToken,
        discountCode: discount || undefined,
      });
    },
    onSuccess: () => {
      toast.success('Purchase submitted successfully. Check your receipts shortly.');
      qc.invalidateQueries({ queryKey: ['history'] });
      navigate('/history');
    },
    onError: (err) => {
      const code = apiErrorCode(err);

      if (code === TICKET_ISSUANCE_FAILURE) {
        const eventId = orderQ.data?.eventId;

        qc.invalidateQueries({ queryKey: ['order', orderId] });
        qc.invalidateQueries({ queryKey: ['zone-seats'] });

        if (eventId) {
          qc.invalidateQueries({ queryKey: ['event', eventId] });
        }

        setCancelledCheckoutPopup({
          eventId,
          message: 'Ticket Issuance Failure has occurred!\nA refund was requested for this purchase'
        });

        return;
      }

      if (code === PURCHASE_POLICY_VIOLATION) {
        setPolicyNotice(purchasePolicyReason(err));
        return;
      }

      toast.error(friendlyError(err, "Checkout couldn't be completed."));
    },
  });

  const applyDiscount = useMutation({
    mutationFn: () => ordersApi.applyDiscount(orderId, { discountCode: discount.trim() }),
    onSuccess: (data) => {
      setPricingPreview(data);
      toast.success('Discount applied successfully. Your totals were updated.');
    },
    onError: (err) => {
      setPricingPreview(null);
      toast.error(friendlyError(err, 'Discount code is invalid or not applicable.'));
    },
  });

  const baseSubtotal = useMemo(() => {
    if (!orderQ.data) return { amount: 0, currency: 'USD' };
    const items = orderQ.data.items;
    const total = items.reduce((sum, i) => sum + i.unitPrice.amount * (i.quantity || 1), 0);
    const currency = items[0]?.unitPrice.currency ?? 'USD';
    return { amount: total, currency };
  }, [orderQ.data]);

  useEffect(() => {
    setPricingPreview(null);
  }, [orderQ.data?.version]);

  if (orderQ.isLoading) return <p>Loading…</p>;
  if (orderQ.isError || !orderQ.data) return <p className="empty">Order not found.</p>;

  const order = orderQ.data;
  const event = eventQ.data;
  const firstDate = event?.dates[0];

  const selectedZone = event?.zones.find((z) => z.zoneId === zoneId);
  const isSeated = selectedZone?.zoneType === 'SEATED';

  const summary = {
    subtotal: pricingPreview?.subtotal ?? baseSubtotal.amount,
    discount: pricingPreview?.discount ?? 0,
    total: pricingPreview?.total ?? baseSubtotal.amount,
    currency: pricingPreview?.currency ?? baseSubtotal.currency,
  };

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

      {policyNotice && (
        <button
          type="button"
          onClick={() => setPolicyNotice(null)}
          title="Click to dismiss"
          style={{
          position: 'fixed',
          left: '50%',
          top: '22%',
          transform: 'translateX(-50%)',
          width: 'min(460px, calc(100vw - 2rem))',
            zIndex: 1001,
            textAlign: 'left',
            cursor: 'pointer',
            border: '1px solid rgba(245, 158, 11, 0.8)',
            borderRadius: '12px',
            padding: '0.85rem 1rem',
            background: 'rgba(30, 25, 15, 0.86)',
            color: 'var(--text)',
            boxShadow: '0 10px 28px rgba(0, 0, 0, 0.28)',
            backdropFilter: 'blur(6px)',
          }}
        >
          <div style={{ fontWeight: 700, marginBottom: '0.35rem' }}>
            Purchase policy violation
          </div>

          <div style={{ fontSize: '0.9rem', lineHeight: 1.4 }}>
            {policyNotice}
          </div>

          <div className="meta" style={{ marginTop: '0.45rem', marginBottom: 0 }}>
            Click to dismiss. You can modify your cart and try again.
          </div>
        </button>
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
              const seatDisplay = zone?.zoneType === 'SEATED'
                ? (seatLabelsQ.data?.get(i.seatId) ?? i.seatId)
                : (i.seatId || 'General admission');
              return (
                <tr key={`${i.zoneId}-${i.seatId}`}>
                  <td>{zone?.zoneName ?? i.zoneId}</td>
                  <td>{seatDisplay}</td>
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
        <span>{formatMoney(summary.subtotal, summary.currency)}</span>
      </div>

      {summary.discount > 0 && (
        <div className="totals">
          <span>Discount</span>
          <span>-{formatMoney(summary.discount, summary.currency)}</span>
        </div>
      )}

      <div className="totals">
        <span>Total</span>
        <span>{formatMoney(summary.total, summary.currency)}</span>
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
        <div className="payment-section">
          <h3>Credit Card Details</h3>

          <label>
            Card Number
            <input
              value={cardNumber}
              onChange={(e) => setCardNumber(e.target.value)}
              placeholder="4111111111111111"
              required
            />
          </label>

          <div className="row">
            <label>
              Month
              <input
                value={month}
                onChange={(e) => setMonth(e.target.value)}
                placeholder="12"
                required
              />
            </label>

            <label>
              Year
              <input
                value={year}
                onChange={(e) => setYear(e.target.value)}
                placeholder="2030"
                required
              />
            </label>

            <label>
              CVV
              <input
                value={cvv}
                onChange={(e) => setCvv(e.target.value)}
                placeholder="123"
                required
              />
            </label>
          </div>

          <label>
            Card Holder
            <input
              value={holder}
              onChange={(e) => setHolder(e.target.value)}
              placeholder="Test Buyer"
              required
            />
          </label>

          <label>
            ID
            <input
              value={id}
              onChange={(e) => setId(e.target.value)}
              placeholder="123456789"
              required
            />
          </label>
        </div>

        <label>
          Discount code (optional)
          <input
            value={discount}
            onChange={(e) => {
              setDiscount(e.target.value);
              setPricingPreview(null);
            }}
            placeholder="PROMO10"
          />
        </label>

        <button
          className="btn ghost"
          type="button"
          onClick={() => applyDiscount.mutate()}
          disabled={applyDiscount.isPending || order.items.length === 0 || !discount.trim()}
        >
          {applyDiscount.isPending ? 'Applying…' : 'Apply Discount'}
        </button>

        <button
          className="btn success"
          type="submit"
          disabled={checkout.isPending || order.items.length === 0 || !!cancelledCheckoutPopup}
        >
          {checkout.isPending ? 'Processing…' : `Pay ${formatMoney(summary.total, summary.currency)}`}
        </button>
      </form>

      <p style={{ marginTop: '1.5rem' }}>
        <Link to="/events" className="btn ghost">Back to catalog</Link>
      </p>
    </section>
  );
}