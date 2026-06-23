import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { eventsApi, type SeatDto } from '../api/endpoints/events';
import './common.css';

interface SeatMapProps {
    zoneId: string;
    zoneName: string;
    price: number;
    currency: string;
    capacity: number;
    onSeatToggle: (seatId: string, isSelected: boolean) => void;
    isLoading: boolean;
}

/**
 * Renders a venue seat map for a SEATED zone using the real per-seat status from
 * the backend (AVAILABLE / RESERVED / SOLD). Taken seats are greyed-out and not
 * clickable; available seats can be selected to add/remove from the cart.
 */
export function InteractiveSeatMap({ zoneId, zoneName, price, currency, onSeatToggle, isLoading }: SeatMapProps) {
    const [selectedSeats, setSelectedSeats] = useState<Set<string>>(new Set());

    const seatsQ = useQuery({
        queryKey: ['zone-seats', zoneId],
        queryFn: () => eventsApi.zoneSeats(zoneId),
        enabled: !!zoneId,
    });

    const toggleSeat = (seat: SeatDto) => {
        if (isLoading || seat.status !== 'AVAILABLE') return;

        const next = new Set(selectedSeats);
        const willSelect = !next.has(seat.seatId);
        if (willSelect) next.add(seat.seatId);
        else next.delete(seat.seatId);

        setSelectedSeats(next);
        onSeatToggle(seat.seatId, willSelect);
    };

    if (seatsQ.isLoading) {
        return <p className="meta" style={{ marginTop: '1.5rem' }}>Loading seat map…</p>;
    }
    if (seatsQ.isError || !seatsQ.data) {
        return <p className="empty" style={{ marginTop: '1.5rem' }}>Couldn't load the seat map.</p>;
    }

    const seats = seatsQ.data.seats;
    if (seats.length === 0) {
        return <p className="empty" style={{ marginTop: '1.5rem' }}>No seats defined for this zone.</p>;
    }

    // Group by row so each row renders on its own line, ordered by seat number.
    const rows = new Map<string, SeatDto[]>();
    for (const seat of seats) {
        if (!rows.has(seat.rowLabel)) rows.set(seat.rowLabel, []);
        rows.get(seat.rowLabel)!.push(seat);
    }
    const orderedRows = [...rows.entries()].sort(([a], [b]) => a.localeCompare(b));
    for (const [, rowSeats] of orderedRows) rowSeats.sort((a, b) => a.seatNumber - b.seatNumber);

    const colorFor = (seat: SeatDto): { bg: string; border: string; cursor: string } => {
        if (selectedSeats.has(seat.seatId)) return { bg: '#4CAF50', border: '2px solid #2E7D32', cursor: 'pointer' };
        switch (seat.status) {
            case 'RESERVED': return { bg: '#5a4a1a', border: '1px solid #8a6d1a', cursor: 'not-allowed' };
            case 'SOLD': return { bg: '#3a1f1f', border: '1px solid #7a2e2e', cursor: 'not-allowed' };
            default: return { bg: '#222', border: '1px solid #555', cursor: 'pointer' };
        }
    };

    return (
        <div style={{ marginTop: '2rem', textAlign: 'center' }}>
            <h3>Seat map — {zoneName}</h3>
            <p className="meta">Price per seat: {price} {currency}</p>

            <div style={{ width: '80%', height: '40px', background: '#333', color: 'white', margin: '20px auto', borderRadius: '8px', lineHeight: '40px' }}>
                STAGE
            </div>

            <div style={{ display: 'inline-flex', flexDirection: 'column', gap: '8px', margin: '0 auto' }}>
                {orderedRows.map(([rowLabel, rowSeats]) => (
                    <div key={rowLabel} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span style={{ width: '1.5rem', color: '#9aa', fontSize: '0.8rem' }}>{rowLabel}</span>
                        {rowSeats.map((seat) => {
                            const c = colorFor(seat);
                            const taken = seat.status !== 'AVAILABLE' && !selectedSeats.has(seat.seatId);
                            return (
                                <div
                                    key={seat.seatId}
                                    onClick={() => toggleSeat(seat)}
                                    title={`Seat ${seat.rowLabel}${seat.seatNumber} — ${seat.status.toLowerCase()}`}
                                    style={{
                                        width: '2.2rem',
                                        padding: '8px 0',
                                        borderRadius: '5px',
                                        cursor: isLoading ? 'wait' : c.cursor,
                                        border: c.border,
                                        backgroundColor: c.bg,
                                        color: 'white',
                                        fontSize: '0.75rem',
                                        opacity: taken ? 0.55 : 1,
                                    }}
                                >
                                    {seat.seatNumber}
                                </div>
                            );
                        })}
                    </div>
                ))}
            </div>

            <div style={{ display: 'flex', justifyContent: 'center', gap: '1.2rem', marginTop: '1rem', fontSize: '0.8rem', color: '#9aa' }}>
                <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#222', border: '1px solid #555', borderRadius: 3, marginRight: 4 }} />Available</span>
                <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#4CAF50', borderRadius: 3, marginRight: 4 }} />Selected</span>
                <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#5a4a1a', borderRadius: 3, marginRight: 4 }} />Reserved</span>
                <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#3a1f1f', borderRadius: 3, marginRight: 4 }} />Sold</span>
            </div>
        </div>
    );
}