import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';
import { eventsApi } from '../../api/endpoints/events';
import { useCompanyPermissions } from '../../auth/useCompanyPermissions';
import { friendlyError } from '../../lib/errors';
import '../../components/common.css';

/** Strip the seconds/zone bits the datetime-local input omits so the backend gets ISO LocalDateTime. */
function toLocalInput(iso: string): string {
  // "2026-09-01T20:00:00" -> "2026-09-01T20:00"
  return iso.length >= 16 ? iso.slice(0, 16) : iso;
}

export function EditEventPage() {
  const { eventId = '' } = useParams();
  const navigate = useNavigate();

  const ev = useQuery({
    queryKey: ['event', eventId],
    queryFn: () => eventsApi.get(eventId),
    enabled: !!eventId,
  });

  const perms = useCompanyPermissions(ev.data?.companyId);

  const [eventName, setEventName] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('');
  const [location, setLocation] = useState('');
  const [dateTime, setDateTime] = useState('');

  useEffect(() => {
    if (!ev.data) return;
    setEventName(ev.data.eventName);
    setDescription(ev.data.description ?? '');
    setCategory(ev.data.category ?? '');
    setLocation(ev.data.location ?? '');
    setDateTime(ev.data.dates[0] ? toLocalInput(ev.data.dates[0]) : '');
  }, [ev.data]);

  const save = useMutation({
    mutationFn: () => {
      const dt = dateTime.length === 16 ? `${dateTime}:00` : dateTime;
      return eventsApi.updateDetails(eventId, {
        eventName,
        dates: [dt],
        category: category || undefined,
        location: location || undefined,
        description: description || undefined,
      });
    },
    onSuccess: () => {
      toast.success('Event details were updated successfully.');
      navigate(`/events/${eventId}`);
    },
    onError: (err) => toast.error(friendlyError(err, "Couldn't update the event.")),
  });

  if (ev.isLoading) return <p>Loading…</p>;
  if (ev.isError || !ev.data) return <p className="empty">Event not found.</p>;

  // Only owners/managers with inventory permission may edit. Others are redirected message.
  if (!perms.loading && !perms.can('EVENT_INVENTORY_MANAGEMENT')) {
    return (
      <section>
        <Link to={`/events/${eventId}`} className="btn ghost">← Event</Link>
        <p className="empty" style={{ marginTop: '1rem' }}>
          You don't have permission to edit this event.
        </p>
      </section>
    );
  }

  return (
    <section>
      <Link to={`/events/${eventId}`} className="btn ghost" style={{ marginBottom: '1rem' }}>← Event</Link>
      <h1 className="page-title">Edit event</h1>

      <form
        className="form-stack"
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate();
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
        <button type="submit" className="btn success" disabled={save.isPending}>
          {save.isPending ? 'Saving…' : 'Save changes'}
        </button>
      </form>
    </section>
  );
}
