import type { PurchaseNode, PurchasePredicate } from '../../types/policies';
import './policies.css';

interface Props {
  node: PurchaseNode;
  onChange: (n: PurchaseNode) => void;
  onRemove?: () => void;
}

const PREDICATE_TEMPLATES: { type: PurchasePredicate['type']; label: string; defaultValue: number }[] = [
  { type: 'minAge', label: 'Min age', defaultValue: 18 },
  { type: 'minTickets', label: 'Min tickets', defaultValue: 1 },
  { type: 'maxTickets', label: 'Max tickets', defaultValue: 5 },
];

const PURCHASE_TEMPLATES: { kind: string; label: string; make: () => PurchaseNode }[] = [
  ...PREDICATE_TEMPLATES.map((p) => ({
    kind: `pred-${p.type}`,
    label: p.label,
    make: (): PurchaseNode => ({
      kind: 'predicate',
      predicate: { type: p.type, value: p.defaultValue },
    }),
  })),
  {
    kind: 'composite',
    label: 'Group (AND/OR)',
    make: () => ({ kind: 'composite', op: 'AND', children: [] }),
  },
];

export function PurchaseNodeEditor({ node, onChange, onRemove }: Props) {
  return (
    <div className="tree-node">
      <div className="node-head">
        <span className="kind-pill">{labelFor(node)}</span>
        <span className="spacer" />
        {onRemove && (
          <button type="button" className="btn ghost" onClick={onRemove}>
            Remove
          </button>
        )}
      </div>
      {renderBody(node, onChange)}
    </div>
  );
}

function labelFor(n: PurchaseNode): string {
  if (n.kind === 'predicate') {
    const t = PREDICATE_TEMPLATES.find((p) => p.type === n.predicate.type);
    return t?.label ?? n.predicate.type;
  }
  return `Group · ${n.op}`;
}

function renderBody(node: PurchaseNode, onChange: (n: PurchaseNode) => void): React.ReactNode {
  if (node.kind === 'predicate') {
    return (
      <div className="fields">
        <label>
          Predicate
          <select
            value={node.predicate.type}
            onChange={(e) => {
              const t = e.target.value as PurchasePredicate['type'];
              const tpl = PREDICATE_TEMPLATES.find((p) => p.type === t)!;
              onChange({ kind: 'predicate', predicate: { type: t, value: tpl.defaultValue } });
            }}
          >
            {PREDICATE_TEMPLATES.map((p) => (
              <option key={p.type} value={p.type}>{p.label}</option>
            ))}
          </select>
        </label>
        <label>
          Value
          <input
            type="number"
            min={0}
            value={node.predicate.value}
            onChange={(e) =>
              onChange({
                kind: 'predicate',
                predicate: { ...node.predicate, value: Number(e.target.value) },
              })
            }
          />
        </label>
      </div>
    );
  }
  return (
    <>
      <div className="fields">
        <label>
          Combine using
          <select
            value={node.op}
            onChange={(e) => onChange({ ...node, op: e.target.value as 'AND' | 'OR' })}
          >
            <option value="AND">AND (all must hold)</option>
            <option value="OR">OR (at least one)</option>
          </select>
        </label>
      </div>
      <div className="tree-children">
        {node.children.map((child, i) => (
          <PurchaseNodeEditor
            key={i}
            node={child}
            onChange={(next) => {
              const children = node.children.slice();
              children[i] = next;
              onChange({ ...node, children });
            }}
            onRemove={() => {
              const children = node.children.slice();
              children.splice(i, 1);
              onChange({ ...node, children });
            }}
          />
        ))}
        <AddPurchaseChild
          onAdd={(child) => onChange({ ...node, children: [...node.children, child] })}
        />
      </div>
    </>
  );
}

function AddPurchaseChild({ onAdd }: { onAdd: (n: PurchaseNode) => void }) {
  return (
    <div className="tree-add">
      <span>+ Add</span>
      {PURCHASE_TEMPLATES.map((t) => (
        <button
          key={t.kind}
          type="button"
          className="btn ghost"
          onClick={() => onAdd(t.make())}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

export const purchaseTemplates = PURCHASE_TEMPLATES;
