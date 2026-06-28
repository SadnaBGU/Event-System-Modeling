import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';
import type { PolicyBundle, PurchaseNode } from '../../types/policies';
import { policiesApi, type DiscountItemRequest } from '../../api/endpoints/policies';
import { eventsApi } from '../../api/endpoints/events';
import { useCompanyPermissions } from '../../auth/useCompanyPermissions';
import { PurchaseNodeEditor, purchaseTemplates } from './PurchaseTreeEditor';
import { previewPurchase } from './preview';
import { friendlyError } from '../../lib/errors';
import '../../components/common.css';
import './policies.css';

interface Props {
  scope: 'company' | 'event';
}

type DiscountKind = 'visible' | 'conditional' | 'coupon';
interface DiscountDraft {
  name: string;
  percent: number;
  kind: DiscountKind;
  code: string;
  minTickets: number;
  endDate: string;
}

function newDiscount(): DiscountDraft {
  return { name: '', percent: 10, kind: 'visible', code: '', minTickets: 2, endDate: '' };
}

export function PolicyEditorPage({ scope }: Props) {
  const params = useParams();
  const id = scope === 'company' ? (params.companyId ?? '') : (params.eventId ?? '');

  const query = useQuery({
    queryKey: ['policies', scope, id],
    queryFn: () =>
      scope === 'company' ? policiesApi.getCompany(id) : policiesApi.getEvent(id),
    enabled: !!id,
  });

  // V3: event policies may only be edited while the event is a draft (before publish).
  const eventQ = useQuery({
    queryKey: ['event', id],
    queryFn: () => eventsApi.get(id),
    enabled: scope === 'event' && !!id,
  });
  const eventLocked = scope === 'event' && !!eventQ.data && eventQ.data.status !== 'DRAFT';

  // Authorization is derived from the backend (per-company roles), not client state:
  // company scope uses the company id directly; event scope resolves it from the event.
  const companyId = scope === 'company' ? id : eventQ.data?.companyId;
  const perms = useCompanyPermissions(companyId);

  const [bundle, setBundle] = useState<PolicyBundle>({ discount: null, purchase: null });

  useEffect(() => {
    if (query.data) setBundle(query.data);
  }, [query.data]);

  const save = useMutation({
    mutationFn: () =>
      scope === 'company'
        ? policiesApi.putCompany(id, bundle)
        : policiesApi.putEvent(id, bundle),
    onSuccess: () => toast.success('Purchase policy saved'),
    onError: (err) => toast.error(friendlyError(err, "Couldn't save the purchase policy.")),
  });

  // ── Discount policy state ──────────────────────────────────────────────────
  const [discounts, setDiscounts] = useState<DiscountDraft[]>([]);
  const [stackable, setStackable] = useState(false);

  const saveDiscounts = useMutation({
    mutationFn: () => {
      const payload = {
        policyName: scope === 'company' ? 'Company discounts' : 'Event discounts',
        stackable,
        discounts: discounts.map<DiscountItemRequest>((d) => ({
          name: d.name.trim(),
          percent: d.percent,
          code: d.kind === 'coupon' && d.code.trim() ? d.code.trim() : undefined,
          minTickets: d.kind === 'conditional' ? d.minTickets : undefined,
          endDate: d.endDate || undefined,
        })),
      };
      return scope === 'company'
        ? policiesApi.putCompanyDiscount(id, payload)
        : policiesApi.putEventDiscount(id, payload);
    },
    onSuccess: () => toast.success('Discount policy saved'),
    onError: (err) => toast.error(friendlyError(err, "Couldn't save the discount policy.")),
  });

  const discountsValid = discounts.length > 0 && discounts.every(
    (d) => d.name.trim() && d.percent > 0 && d.percent <= 100 &&
      (d.kind !== 'coupon' || d.code.trim()),
  );

  if (query.isLoading || (scope === 'event' && eventQ.isLoading) || perms.loading) {
    return <p>Loading…</p>;
  }

  if (!perms.can('MODIFY_POLICIES')) {
    return (
      <section>
        <Link
          to={scope === 'company' ? `/companies/${id}` : `/events/${id}`}
          className="btn ghost"
          style={{ marginBottom: '1rem' }}
        >
          ← Back
        </Link>
        <h1 className="page-title">{scope === 'company' ? 'Company policies' : 'Event policies'}</h1>
        <p className="empty">You don't have permission to edit these policies.</p>
      </section>
    );
  }

  if (eventLocked) {
    return (
      <section>
        <Link to={`/events/${id}`} className="btn ghost" style={{ marginBottom: '1rem' }}>
          ← Back
        </Link>
        <h1 className="page-title">Event policies</h1>
        <p className="empty">
          This event is published. Policies can only be edited while it is a draft.
        </p>
      </section>
    );
  }

  return (
    <section>
      <Link
        to={scope === 'company' ? `/companies/${id}` : `/events/${id}`}
        className="btn ghost"
        style={{ marginBottom: '1rem' }}
      >
        ← Back
      </Link>
      <h1 className="page-title">
        {scope === 'company' ? 'Company policies' : 'Event policies'}
      </h1>

      <h2 style={{ fontSize: '1.05rem', marginTop: '1rem' }}>Purchase policy</h2>
      <p className="meta">Who may buy, and how many (e.g. max tickets per buyer, minimum age).</p>
      {bundle.purchase ? (
        <PurchaseNodeEditor
          node={bundle.purchase}
          onChange={(purchase) => setBundle((b) => ({ ...b, purchase }))}
          onRemove={() => setBundle((b) => ({ ...b, purchase: null }))}
        />
      ) : (
        <EmptyTree
          label="No purchase policy yet (anyone can buy)."
          templates={purchaseTemplates.map((t) => ({ label: t.label, make: t.make }))}
          onPick={(node) => setBundle((b) => ({ ...b, purchase: node as PurchaseNode }))}
        />
      )}
      <div className="preview-box">
        <div className="label">Preview</div>
        {previewPurchase(bundle.purchase)}
      </div>

      <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
        <button
          type="button"
          className="btn success"
          onClick={() => save.mutate()}
          disabled={save.isPending || !bundle.purchase}
        >
          {save.isPending ? 'Saving…' : 'Save purchase policy'}
        </button>
      </div>

      {/* ── Discount policy ── */}
      <h2 style={{ fontSize: '1.05rem', marginTop: '2rem' }}>Discount policy</h2>
      <p className="meta">
        Visible discounts (e.g. Early Bird), conditional offers (buy N+), or hidden coupon codes.
      </p>

      {discounts.length === 0 && (
        <p className="empty">No discounts yet.</p>
      )}

      {discounts.map((d, i) => (
        <div key={i} className="zone-row" style={{ flexDirection: 'column', alignItems: 'stretch', gap: '0.5rem' }}>
          <div className="form-stack">
            <label>
              Name
              <input
                value={d.name}
                onChange={(e) => updateDiscount(setDiscounts, i, { name: e.target.value })}
                placeholder="Early Bird"
              />
            </label>
            <label>
              Percent off (1–100)
              <input
                type="number"
                min={1}
                max={100}
                value={d.percent}
                onChange={(e) => updateDiscount(setDiscounts, i, { percent: Number(e.target.value) })}
              />
            </label>
            <label>
              Type
              <select
                value={d.kind}
                onChange={(e) => updateDiscount(setDiscounts, i, { kind: e.target.value as DiscountKind })}
              >
                <option value="visible">Visible (applies to everyone)</option>
                <option value="conditional">Conditional (min tickets)</option>
                <option value="coupon">Coupon code (hidden)</option>
              </select>
            </label>
            {d.kind === 'conditional' && (
              <label>
                Minimum tickets
                <input
                  type="number"
                  min={1}
                  value={d.minTickets}
                  onChange={(e) => updateDiscount(setDiscounts, i, { minTickets: Number(e.target.value) })}
                />
              </label>
            )}
            {d.kind === 'coupon' && (
              <label>
                Coupon code
                <input
                  value={d.code}
                  onChange={(e) => updateDiscount(setDiscounts, i, { code: e.target.value })}
                  placeholder="PROMO10"
                />
              </label>
            )}
            <label>
              Ends on (optional)
              <input
                type="date"
                value={d.endDate}
                onChange={(e) => updateDiscount(setDiscounts, i, { endDate: e.target.value })}
              />
            </label>
          </div>
          <button
            type="button"
            className="btn ghost"
            style={{ alignSelf: 'flex-start' }}
            onClick={() => setDiscounts((arr) => arr.filter((_, j) => j !== i))}
          >
            Remove discount
          </button>
        </div>
      ))}

      <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.75rem', flexWrap: 'wrap', alignItems: 'center' }}>
        <button
          type="button"
          className="btn ghost"
          onClick={() => setDiscounts((arr) => [...arr, newDiscount()])}
        >
          + Add discount
        </button>
        <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
          <input type="checkbox" checked={stackable} onChange={(e) => setStackable(e.target.checked)} />
          Allow stacking multiple discounts
        </label>
        <button
          type="button"
          className="btn success"
          onClick={() => saveDiscounts.mutate()}
          disabled={saveDiscounts.isPending || !discountsValid}
        >
          {saveDiscounts.isPending ? 'Saving…' : 'Save discount policy'}
        </button>
      </div>
    </section>
  );
}

function updateDiscount(
  setDiscounts: React.Dispatch<React.SetStateAction<DiscountDraft[]>>,
  index: number,
  patch: Partial<DiscountDraft>,
) {
  setDiscounts((arr) => arr.map((d, i) => (i === index ? { ...d, ...patch } : d)));
}

function EmptyTree<T>({
  label,
  templates,
  onPick,
}: {
  label: string;
  templates: { label: string; make: () => T }[];
  onPick: (node: T) => void;
}) {
  return (
    <div className="tree-empty">
      <p style={{ marginTop: 0 }}>{label}</p>
      <div className="tree-add" style={{ justifyContent: 'center' }}>
        <span>+ Start with</span>
        {templates.map((t) => (
          <button
            key={t.label}
            type="button"
            className="btn ghost"
            onClick={() => onPick(t.make())}
          >
            {t.label}
          </button>
        ))}
      </div>
    </div>
  );
}
