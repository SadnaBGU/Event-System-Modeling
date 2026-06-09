import React, { useState } from 'react';
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

export function InteractiveSeatMap({ zoneId, zoneName, price, currency, capacity, onSeatToggle, isLoading }: SeatMapProps) {
    const [selectedSeats, setSelectedSeats] = useState<Set<string>>(new Set());

    const seats = Array.from({ length: Math.min(capacity, 100) }, (_, i) => {
        const row = String.fromCharCode(65 + Math.floor(i / 10)); // A, B, C...
        const col = (i % 10) + 1;
        return { id: `${row}-${col}`, row, col };
    });

    const toggleSeat = (seatId: string) => {
        if (isLoading) return;
        
        const newSelected = new Set(selectedSeats);
        const willBeSelected = !newSelected.has(seatId);

        if (willBeSelected) {
            newSelected.add(seatId);
        } else {
            newSelected.delete(seatId);
        }
        
        setSelectedSeats(newSelected);
        onSeatToggle(seatId, willBeSelected);
    };

    return (
        <div style={{ marginTop: '2rem', textAlign: 'center' }}>
            <h3> Venue Map: region {zoneName}</h3>
            <p className="meta">Price per ticket: {price} {currency}</p>
            
            <div style={{ width: '80%', height: '40px', background: '#333', color: 'white', margin: '20px auto', borderRadius: '8px', lineHeight: '40px' }}>
                STAGE
            </div>
            
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(10, 1fr)', gap: '10px', maxWidth: '500px', margin: '0 auto' }}>
                {seats.map(seat => {
                    const isSelected = selectedSeats.has(seat.id);
                    return (
                        <div 
                            key={seat.id}
                            onClick={() => toggleSeat(seat.id)}
                            style={{
                                padding: '10px 0',
                                borderRadius: '5px',
                                cursor: isLoading ? 'wait' : 'pointer',
                                border: isSelected ? '2px solid #2E7D32' : '1px solid #555',
                                backgroundColor: isSelected ? '#4CAF50' : '#222',
                                color: 'white',
                                fontSize: '0.85rem'
                            }}
                            title={`Seat ${seat.id}`}
                        >
                            {seat.id}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}