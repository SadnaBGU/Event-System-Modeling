import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { eventsMgmtApi } from '../../api/endpoints/eventsMgmt';
import type { CreateEventRequest } from '../../types/api';
import '../../components/common.css';

interface ZoneDraft {
  name: string;
  type: 'SEATED' | 'STANDING';
  basePrice: number;
  capacity: number;
}

export function CreateEventPage() {
  const { companyId = '' } = useParams();
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [dateTime, setDateTime] = useState('');
  const [venueName, setVenueName] = useState('');
  const [zones, setZones] = useState<ZoneDraft[]>([
    { name: 'General', type: 'STANDING', basePrice: 50, capacity: 100 },
  ]);

  const create = useMutation({
    mutationFn: () => {
      const body: CreateEventRequest = {
        name,
        description: description || undefined,
        dateTime: new Date(dateTime).toISOString(),
        venueName,
        zones,
      };
      return eventsMgmtApi.create(companyId, body);
    },
    onSuccess: (event) => {
      toast.success('Event created');
      navigate(`/events/${event.eventId}`);
    },
  });

  function updateZone(i: number, patch: Partial<ZoneDraft>) {
    setZones((cur) => cur.map((z, idx) => (idx === i ? { ...z, ...patch } : z)));
  }

  return (
    <section>
      <Link to={`/companies/${companyId}`} className="btn ghost" style={{ marginBottom: '1rem' }}>
        ← Company
      </Link>
      <h1 className="page-title">Create event</h1>

      <form
        className="form-stack"
        onSubmit={(e) => {
          e.preventDefault();
          create.mutate();
        }}
      >
        <label>
          Event name
          <input value={name} onChange={(e) => setName(e.target.value)} required />
        </label>
        <label>
          Description
          <input value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        <label>
          Date &amp; time
          <input
            type="datetime-local"
            value={dateTime}
            onChange={(e) => setDateTime(e.target.value)}
            required
          />
        </label>
        <label>
          Venue
          <input value={venueName} onChange={(e) => setVenueName(e.target.value)} required />
        </label>

        <h2 style={{ fontSize: '1rem', marginTop: '1rem' }}>Zones</h2>
        {zones.map((z, i) => (
          <div className="zone-row" key={i}>
            <div className="zone-info" style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
              <input
                value={z.name}
                onChange={(e) => updateZone(i, { name: e.target.value })}
                placeholder="Name"
                required
                style={{ background: '#0d1117', color: '#e6edf3', border: '1px solid #30363d', borderRadius: 4, padding: '0.3rem 0.5rem' }}
              />
              <select
                value={z.type}
                onChange={(e) => updateZone(i, { type: e.target.value as 'SEATED' | 'STANDING' })}
                style={{ background: '#0d1117', color: '#e6edf3', border: '1px solid #30363d', borderRadius: 4, padding: '0.3rem 0.5rem' }}
              >
                <option value="STANDING">Standing</option>
                <option value="SEATED">Seated</option>
              </select>
              <input
                type="number"
                min={0}
                value={z.basePrice}
                onChange={(e) => updateZone(i, { basePrice: Number(e.target.value) })}
                placeholder="Price"
                required
                style={{ width: 100, background: '#0d1117', color: '#e6edf3', border: '1px solid #30363d', borderRadius: 4, padding: '0.3rem 0.5rem' }}
              />
              <input
                type="number"
                min={1}
                value={z.capacity}
                onChange={(e) => updateZone(i, { capacity: Number(e.target.value) })}
                placeholder="Capacity"
                required
                style={{ width: 100, background: '#0d1117', color: '#e6edf3', border: '1px solid #30363d', borderRadius: 4, padding: '0.3rem 0.5rem' }}
              />
            </div>
            <button
              type="button"
              className="btn ghost"
              onClick={() => setZones((cur) => cur.filter((_, idx) => idx !== i))}
              disabled={zones.length === 1}
            >
              Remove
            </button>
          </div>
        ))}
        <button
          type="button"
          className="btn ghost"
          onClick={() =>
            setZones((cur) => [
              ...cur,
              { name: '', type: 'STANDING', basePrice: 0, capacity: 100 },
            ])
          }
        >
          + Add zone
        </button>

        <button type="submit" className="btn success" disabled={create.isPending}>
          {create.isPending ? 'Creating…' : 'Create event'}
        </button>
      </form>
    </section>
  );
}
