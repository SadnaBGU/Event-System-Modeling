import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';
import type { DiscountNode, PolicyBundle, PurchaseNode } from '../../types/policies';
import { policiesApi } from '../../api/endpoints/policies';
import {
  DiscountNodeEditor,
  discountTemplates,
} from './DiscountTreeEditor';
import { PurchaseNodeEditor, purchaseTemplates } from './PurchaseTreeEditor';
import { previewDiscount, previewPurchase } from './preview';
import '../../components/common.css';
import './policies.css';

interface Props {
  scope: 'company' | 'event';
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

  const [bundle, setBundle] = useState<PolicyBundle>({ discount: null, purchase: null });

  useEffect(() => {
    if (query.data) setBundle(query.data);
  }, [query.data]);

  const save = useMutation({
    mutationFn: () =>
      scope === 'company'
        ? policiesApi.putCompany(id, bundle)
        : policiesApi.putEvent(id, bundle),
    onSuccess: () => toast.success('Policies saved'),
  });

  if (query.isLoading) return <p>Loading…</p>;

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

      <h2 style={{ fontSize: '1.05rem', marginTop: '1rem' }}>Discount policy</h2>
      {bundle.discount ? (
        <DiscountNodeEditor
          node={bundle.discount}
          onChange={(discount) => setBundle((b) => ({ ...b, discount }))}
          onRemove={() => setBundle((b) => ({ ...b, discount: null }))}
        />
      ) : (
        <EmptyTree
          label="No discount policy yet."
          templates={discountTemplates.map((t) => ({ label: t.label, make: t.make }))}
          onPick={(node) => setBundle((b) => ({ ...b, discount: node as DiscountNode }))}
        />
      )}
      <div className="preview-box">
        <div className="label">Preview</div>
        {previewDiscount(bundle.discount)}
      </div>

      <h2 style={{ fontSize: '1.05rem', marginTop: '1.5rem' }}>Purchase policy</h2>
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
          disabled={save.isPending}
        >
          {save.isPending ? 'Saving…' : 'Save policies'}
        </button>
      </div>
    </section>
  );
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
