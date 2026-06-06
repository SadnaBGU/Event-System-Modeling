import type {
  DiscountCondition,
  DiscountNode,
} from '../../types/policies';
import './policies.css';

interface Props {
  node: DiscountNode;
  onChange: (n: DiscountNode) => void;
  onRemove?: () => void;
}

// Factory map for "+ Add child" / "Wrap in composite" actions. Single place to
// register new discount kinds — matches the spec's "easy to add new types".
const DISCOUNT_TEMPLATES: { kind: string; label: string; make: () => DiscountNode }[] = [
  { kind: 'simple', label: 'Simple (%)', make: () => ({ kind: 'simple', percent: 10 }) },
  {
    kind: 'conditional',
    label: 'Conditional',
    make: () => ({
      kind: 'conditional',
      percent: 10,
      condition: { type: 'minTickets', value: 2 },
    }),
  },
  {
    kind: 'coupon',
    label: 'Coupon',
    make: () => ({ kind: 'coupon', percent: 10, code: 'NEW', validUntil: '' }),
  },
  {
    kind: 'composite',
    label: 'Group (MAX/SUM)',
    make: () => ({ kind: 'composite', op: 'SUM', children: [] }),
  },
];

export function DiscountNodeEditor({ node, onChange, onRemove }: Props) {
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

function labelFor(n: DiscountNode): string {
  switch (n.kind) {
    case 'simple': return 'Simple';
    case 'conditional': return 'Conditional';
    case 'coupon': return 'Coupon';
    case 'composite': return `Group · ${n.op}`;
  }
}

function renderBody(node: DiscountNode, onChange: (n: DiscountNode) => void): React.ReactNode {
  switch (node.kind) {
    case 'simple':
      return (
        <div className="fields">
          <label>
            Percent off
            <input
              type="number"
              min={0}
              max={100}
              value={node.percent}
              onChange={(e) => onChange({ ...node, percent: Number(e.target.value) })}
            />
          </label>
        </div>
      );
    case 'conditional':
      return (
        <div className="fields">
          <label>
            Percent off
            <input
              type="number"
              min={0}
              max={100}
              value={node.percent}
              onChange={(e) => onChange({ ...node, percent: Number(e.target.value) })}
            />
          </label>
          <ConditionEditor
            condition={node.condition}
            onChange={(condition) => onChange({ ...node, condition })}
          />
        </div>
      );
    case 'coupon':
      return (
        <div className="fields">
          <label>
            Percent off
            <input
              type="number"
              min={0}
              max={100}
              value={node.percent}
              onChange={(e) => onChange({ ...node, percent: Number(e.target.value) })}
            />
          </label>
          <label>
            Code
            <input
              value={node.code}
              onChange={(e) => onChange({ ...node, code: e.target.value })}
            />
          </label>
          <label>
            Valid until
            <input
              type="date"
              value={node.validUntil}
              onChange={(e) => onChange({ ...node, validUntil: e.target.value })}
            />
          </label>
        </div>
      );
    case 'composite':
      return (
        <>
          <div className="fields">
            <label>
              Combine using
              <select
                value={node.op}
                onChange={(e) => onChange({ ...node, op: e.target.value as 'MAX' | 'SUM' })}
              >
                <option value="MAX">MAX (best discount wins)</option>
                <option value="SUM">SUM (stack discounts)</option>
              </select>
            </label>
          </div>
          <div className="tree-children">
            {node.children.map((child, i) => (
              <DiscountNodeEditor
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
            <AddDiscountChild
              onAdd={(child) => onChange({ ...node, children: [...node.children, child] })}
            />
          </div>
        </>
      );
  }
}

function ConditionEditor({
  condition,
  onChange,
}: {
  condition: DiscountCondition;
  onChange: (c: DiscountCondition) => void;
}) {
  return (
    <>
      <label>
        When
        <select
          value={condition.type}
          onChange={(e) => {
            const type = e.target.value as DiscountCondition['type'];
            if (type === 'dateRange') onChange({ type: 'dateRange' });
            else onChange({ type, value: 1 });
          }}
        >
          <option value="minTickets">Min tickets</option>
          <option value="maxTickets">Max tickets</option>
          <option value="dateRange">Date range</option>
        </select>
      </label>
      {condition.type === 'dateRange' ? (
        <>
          <label>
            From
            <input
              type="date"
              value={condition.from ?? ''}
              onChange={(e) => onChange({ ...condition, from: e.target.value || undefined })}
            />
          </label>
          <label>
            Until
            <input
              type="date"
              value={condition.until ?? ''}
              onChange={(e) => onChange({ ...condition, until: e.target.value || undefined })}
            />
          </label>
        </>
      ) : (
        <label>
          Value
          <input
            type="number"
            min={1}
            value={condition.value}
            onChange={(e) => onChange({ ...condition, value: Number(e.target.value) })}
          />
        </label>
      )}
    </>
  );
}

function AddDiscountChild({ onAdd }: { onAdd: (n: DiscountNode) => void }) {
  return (
    <div className="tree-add">
      <span>+ Add</span>
      {DISCOUNT_TEMPLATES.map((t) => (
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

export const discountTemplates = DISCOUNT_TEMPLATES;
