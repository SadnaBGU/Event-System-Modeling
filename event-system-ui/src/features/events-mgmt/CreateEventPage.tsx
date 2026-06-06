import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { eventsMgmtApi } from '../../api/endpoints/eventsMgmt';
import type { CreateEventRequest } from '../../types/api';
import '../../components/common.css';

export function CreateEventPage() {
  const { companyId = '' } = useParams();
  const navigate = useNavigate();

  const [eventName, setEventName] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('');
  const [location, setLocation] = useState('');
  const [dateTime, setDateTime] = useState('');

  const create = useMutation({
    mutationFn: () => {
      // Backend takes LocalDateTime[] without zone offset, so strip the seconds-zone bits.
      const dt = dateTime.length === 16 ? `${dateTime}:00` : dateTime;
      const body: CreateEventRequest = {
        eventName,
        dates: [dt],
        category: category || undefined,
        location: location || undefined,
        description: description || undefined,
      };
      return eventsMgmtApi.create(companyId, body);
    },
    onSuccess: (locationHeader) => {
      toast.success('Event created');
      const newEventId = locationHeader.split('/').pop();
      navigate(newEventId ? `/events/${newEventId}` : `/companies/${companyId}`);
    },
  });

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
          <input value={eventName} onChange={(e) => setEventName(e.target.value)} required />
        </label>
        <label>
          Category
          <input value={category} onChange={(e) => setCategory(e.target.value)} placeholder="e.g. Concert" />
        </label>
        <label>
          Location
          <input value={location} onChange={(e) => setLocation(e.target.value)} placeholder="e.g. Grand Hall" />
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

        <p className="meta">
          Zones are configured separately under the company's venues after the event is created.
        </p>

        <button type="submit" className="btn success" disabled={create.isPending}>
          {create.isPending ? 'Creating…' : 'Create event'}
        </button>
      </form>
    </section>
  );
}
