import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { eventsApi, type EventSearchParams } from '../../api/endpoints/events';
import { formatDateTime, formatMoney } from '../../lib/format';
import '../../components/common.css';

interface DraftFilters {
  artist?: string;
  maxPrice?: number;
}

function toSearchParams(d: DraftFilters): EventSearchParams {
  const params: EventSearchParams = {};
  if (d.artist) params.artist = d.artist;
  if (typeof d.maxPrice === 'number') params.priceRange = `0,${d.maxPrice}`;
  return params;
}

export function CatalogPage() {
  const [filters, setFilters] = useState<DraftFilters>({});
  const [draft, setDraft] = useState<DraftFilters>({});

  const query = useQuery({
    queryKey: ['events', filters],
    queryFn: () => eventsApi.search(toSearchParams(filters)),
  });

  function apply(e: React.FormEvent) {
    e.preventDefault();
    setFilters(draft);
  }

  return (
    <section>
      <h1 className="page-title">Event catalog</h1>
      <form className="filter-bar" onSubmit={apply}>
        <label>
          Artist / company
          <input
            value={draft.artist ?? ''}
            onChange={(e) => setDraft((f) => ({ ...f, artist: e.target.value || undefined }))}
            placeholder="e.g. Stellar"
          />
        </label>
        <label>
          Max price
          <input
            type="number"
            min={0}
            value={draft.maxPrice ?? ''}
            onChange={(e) =>
              setDraft((f) => ({
                ...f,
                maxPrice: e.target.value ? Number(e.target.value) : undefined,
              }))
            }
          />
        </label>
        <button className="btn" type="submit">Apply</button>
        {Object.keys(filters).length > 0 && (
          <button
            className="btn ghost"
            type="button"
            onClick={() => {
              setDraft({});
              setFilters({});
            }}
          >
            Clear
          </button>
        )}
      </form>

      {query.isLoading && <p>Loading…</p>}
      {query.isError && <p className="empty">Could not load events.</p>}
      {query.data && query.data.length === 0 && <p className="empty">No events match your filters.</p>}
      {query.data && query.data.length > 0 && (
        <div className="card-grid">
          {query.data.map((ev) => {
            const firstDate = ev.dates[0];
            const minPrice = ev.priceSummary?.minPrice;
            return (
              <article className="card" key={ev.eventId}>
                <h3>{ev.eventName}</h3>
                <div className="meta">
                  {ev.artist && <div><strong>{ev.artist}</strong></div>}
                  {firstDate && <div>{formatDateTime(firstDate)}</div>}
                  {ev.location && <div>{ev.location}</div>}
                </div>
                {typeof minPrice === 'number' && minPrice > 0 && (
                  <div className="meta">from {formatMoney(minPrice, ev.priceSummary?.currency)}</div>
                )}
                <div className="actions">
                  <Link to={`/events/${ev.eventId}`} className="btn">View</Link>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
