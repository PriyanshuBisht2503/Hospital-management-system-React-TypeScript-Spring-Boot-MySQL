import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { getDoctorSlots } from '../api/doctors';
import { formatIstDateTime } from '../lib/time';
import type { AvailableSlot } from '../types/doctor';
import { Button } from '@hms/ui';

export interface SlotPickerProps {
  doctorId: number;
  branchId: number;
  date: string; // IST date
  selected: AvailableSlot | null;
  onSelect: (slot: AvailableSlot) => void;
}

/** Slots are UTC instants shown in IST; the exact slot instants are passed through unchanged on select. */
export const SlotPicker: React.FC<SlotPickerProps> = ({ doctorId, branchId, date, selected, onSelect }) => {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['doctor-slots', doctorId, branchId, date],
    queryFn: () => getDoctorSlots(doctorId, { branchId, date }),
    enabled: !!doctorId && !!branchId && !!date,
  });

  if (isLoading) return <div style={{ padding: '24px', textAlign: 'center', color: '#64748b' }}>Loading slots...</div>;
  if (isError) {
    return (
      <div style={{ padding: '24px', textAlign: 'center', color: '#ef4444' }}>
        <p>Failed to load slots</p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>Retry</Button>
      </div>
    );
  }
  if (!data || data.length === 0) {
    return <div style={{ padding: '24px', textAlign: 'center', color: '#64748b' }}>No slots this day, try another date.</div>;
  }

  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
      {data.map((slot) => {
        const active = selected?.startTime === slot.startTime;
        return (
          <button
            key={slot.startTime}
            type="button"
            onClick={() => onSelect(slot)}
            style={{
              padding: '8px 12px',
              borderRadius: '8px',
              border: active ? '1.5px solid #0d9488' : '1px solid #cbd5e1',
              backgroundColor: active ? '#0d9488' : '#ffffff',
              color: active ? '#ffffff' : '#0f172a',
              cursor: 'pointer',
              fontSize: '13px',
            }}
          >
            {formatIstDateTime(slot.startTime)} – {formatIstDateTime(slot.endTime)}
          </button>
        );
      })}
    </div>
  );
};
