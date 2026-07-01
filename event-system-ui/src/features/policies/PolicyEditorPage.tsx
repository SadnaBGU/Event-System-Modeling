import { useEffect, useState, type Dispatch, type SetStateAction } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';

import type {
  DiscountPolicySummary,
  PolicyBundle,
  PurchaseNode,
  PurchasePolicySummary,
} from '../../types/policies';
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

const EMPTY_BUNDLE: PolicyBundle = {
  discount: null,
  purchase: null,
  purchasePolicies: [],
  discountPolicies: [],
};

function newDiscount(): DiscountDraft {
  return {
    name: '',
    percent: 10,
    kind: 'visible',
    code: '',
    minTickets: 2,
    endDate: '',
  };
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

  // V3: event policies may only be edited while the event is a draft before publish.
  const eventQ = useQuery({
    queryKey: ['event', id],
    queryFn: () => eventsApi.get(id),
    enabled: scope === 'event' && !!id,
  });

  const eventLocked = scope === 'event' && !!eventQ.data && eventQ.data.status !== 'DRAFT';

  // Authorization is derived from the backend roles.
  // Company scope uses the company id directly; event scope resolves it from the event.
  const companyId = scope === 'company' ? id : eventQ.data?.companyId;
  const perms = useCompanyPermissions(companyId);

  const [bundle, setBundle] = useState<PolicyBundle>(EMPTY_BUNDLE);

  useEffect(() => {
    if (query.data) {
      setBundle({
        ...EMPTY_BUNDLE,
        ...query.data,
        purchasePolicies: query.data.purchasePolicies ?? [],
        discountPolicies: query.data.discountPolicies ?? [],
      });
    }
  }, [query.data]);

  const save = useMutation({
    mutationFn: () =>
      scope === 'company'
        ? policiesApi.putCompany(id, bundle)
        : policiesApi.putEvent(id, bundle),
    onSuccess: async () => {
      toast.success('The purchase policy was saved successfully.');
      await query.refetch();
    },
    onError: (err) => toast.error(friendlyError(err, "Couldn't save the purchase policy.")),
  });

  // ── Discount policy draft state ────────────────────────────────────────────

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
    onSuccess: async () => {
      toast.success('The discount policy was saved successfully.');
      setDiscounts([]);
      await query.refetch();
    },
    onError: (err) => toast.error(friendlyError(err, "Couldn't save the discount policy.")),
  });

  const discountsValid =
    discounts.length > 0 &&
    discounts.every(
      (d) =>
        d.name.trim() &&
        d.percent > 0 &&
        d.percent <= 100 &&
        (d.kind !== 'coupon' || d.code.trim()) &&
        (d.kind !== 'conditional' || d.minTickets > 0),
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

        <h1 className="page-title">
          {scope === 'company' ? 'Company policies' : 'Event policies'}
        </h1>

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

      {/* ── Purchase policy ── */}
      <h2 style={{ fontSize: '1.05rem', marginTop: '1rem' }}>Purchase policy</h2>
      <p className="meta">
        Who may buy, and how many, for example max tickets per buyer or minimum age.
      </p>

      <ExistingPurchasePolicies policies={bundle.purchasePolicies} />

      <h3 className="draft-title">New purchase policy draft</h3>

      {bundle.purchase ? (
        <PurchaseNodeEditor
          node={bundle.purchase}
          onChange={(purchase) => setBundle((b) => ({ ...b, purchase }))}
          onRemove={() => setBundle((b) => ({ ...b, purchase: null }))}
        />
      ) : (
        <EmptyTree
          label="No purchase policy draft selected."
          templates={purchaseTemplates.map((t) => ({ label: t.label, make: t.make }))}
          onPick={(node) => setBundle((b) => ({ ...b, purchase: node as PurchaseNode }))}
        />
      )}

      <div className="preview-box">
        <div className="label">Preview</div>
        {previewPurchase(bundle.purchase)}
      </div>

      <div className="policy-actions">
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
        Visible discounts, conditional offers, or hidden coupon codes.
      </p>

      <ExistingDiscountPolicies policies={bundle.discountPolicies} />

      <h3 className="draft-title">New discount policy draft</h3>

      {discounts.length === 0 && <p className="empty">No discount draft yet.</p>}

      {discounts.map((d, i) => (
        <div key={i} className="zone-row discount-draft-row">
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
                onChange={(e) =>
                  updateDiscount(setDiscounts, i, { percent: Number(e.target.value) })
                }
              />
            </label>

            <label>
              Type
              <select
                value={d.kind}
                onChange={(e) =>
                  updateDiscount(setDiscounts, i, { kind: e.target.value as DiscountKind })
                }
              >
                <option value="visible">Visible, applies to everyone</option>
                <option value="conditional">Conditional, min tickets</option>
                <option value="coupon">Coupon code, hidden</option>
              </select>
            </label>

            {d.kind === 'conditional' && (
              <label>
                Minimum tickets
                <input
                  type="number"
                  min={1}
                  value={d.minTickets}
                  onChange={(e) =>
                    updateDiscount(setDiscounts, i, { minTickets: Number(e.target.value) })
                  }
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

      <div className="discount-actions">
        <button
          type="button"
          className="btn ghost"
          onClick={() => setDiscounts((arr) => [...arr, newDiscount()])}
        >
          + Add discount
        </button>

        <label className="stackable-toggle">
          <input
            type="checkbox"
            checked={stackable}
            onChange={(e) => setStackable(e.target.checked)}
          />
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

function ExistingPurchasePolicies({ policies }: { policies: PurchasePolicySummary[] }) {
  return (
    <section className="saved-policy-section">
      <h3>Saved purchase policies</h3>

      {policies.length === 0 ? (
        <p className="muted">No saved purchase policies returned by the backend.</p>
      ) : (
        <div className="saved-policy-list">
          {policies.map((policy) => (
            <article className="saved-policy-card" key={policy.policyId}>
              <div className="saved-policy-card__head">
                <strong>{policy.policyName || 'Purchase policy'}</strong>
                <span className="kind-pill purchase">PURCHASE</span>
                <span className={policy.active ? 'status-pill active' : 'status-pill inactive'}>
                  {policy.active ? 'Active' : 'Inactive'}
                </span>
              </div>

              <dl>
                <div>
                  <dt>Owner</dt>
                  <dd>{policy.ownerType}</dd>
                </div>

                <div>
                  <dt>Scope</dt>
                  <dd>{formatScope(policy.scope)}</dd>
                </div>

                <div>
                  <dt>Summary</dt>
                  <dd>{policy.summary || 'No summary available.'}</dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function ExistingDiscountPolicies({ policies }: { policies: DiscountPolicySummary[] }) {
  return (
    <section className="saved-policy-section">
      <h3>Saved discount policies</h3>

      {policies.length === 0 ? (
        <p className="muted">No saved discount policies returned by the backend.</p>
      ) : (
        <div className="saved-policy-list">
          {policies.map((policy) => (
            <article className="saved-policy-card" key={policy.policyId}>
              <div className="saved-policy-card__head">
                <strong>{policy.policyName || 'Discount policy'}</strong>
                <span className="kind-pill discount">DISCOUNT</span>
                <span className={policy.active ? 'status-pill active' : 'status-pill inactive'}>
                  {policy.active ? 'Active' : 'Inactive'}
                </span>
              </div>

              <dl>
                <div>
                  <dt>Owner</dt>
                  <dd>{policy.ownerType}</dd>
                </div>

                <div>
                  <dt>Scope</dt>
                  <dd>{formatScope(policy.scope)}</dd>
                </div>

                <div>
                  <dt>Stacking</dt>
                  <dd>{policy.stackable ? 'Allowed' : 'Best discount only'}</dd>
                </div>

                <div>
                  <dt>Summary</dt>
                  <dd>{policy.summary || `${policy.discounts?.length ?? 0} discount(s)`}</dd>
                </div>
              </dl>

              {policy.discounts?.length > 0 && (
                <ul className="discount-summary-list">
                  {policy.discounts.map((discount, index) => (
                    <li key={`${policy.policyId}-${index}`}>
                      <strong>{discount.discountName}</strong>
                      {' · '}
                      {formatPercent(discount.discountPercent)} off
                      {discount.endDate ? ` · until ${discount.endDate}` : ''}
                    </li>
                  ))}
                </ul>
              )}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function formatScope(scope: PurchasePolicySummary['scope']): string {
  if (!scope) return 'Unknown scope';

  const parts: string[] = [];

  if (scope.companyWide) {
    parts.push('Company-wide');
  }

  if (scope.eventIds.length > 0) {
    parts.push(`Events: ${scope.eventIds.join(', ')}`);
  }

  return parts.length > 0 ? parts.join(' + ') : 'No active scope';
}

function formatPercent(value: number | string): string {
  if (typeof value === 'number') {
    return `${value}%`;
  }

  const trimmed = value.trim();
  return trimmed.endsWith('%') ? trimmed : `${trimmed}%`;
}

function updateDiscount(
  setDiscounts: Dispatch<SetStateAction<DiscountDraft[]>>,
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