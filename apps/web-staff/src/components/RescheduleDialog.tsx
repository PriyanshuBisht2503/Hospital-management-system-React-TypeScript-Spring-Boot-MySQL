import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Modal, Button, Select, Input } from '@hms/ui';
import { SlotPicker } from './SlotPicker';
import { listBranches } from '../api/branches';
import { formatIstDateTime, toIstDateString } from '../lib/time';
import type { AvailableSlot } from '../types/doctor';
import type { RescheduleAppointmentRequest } from '../types/appointment';

export interface RescheduleDialogProps {
  isOpen: boolean;
  isLoading: boolean;
  doctorId: number;
  /** The branch already on the appointment (used as default). */
  currentBranchId: number;
  onConfirm: (req: RescheduleAppointmentRequest) => void;
  onClose: () => void;
  /** Error message from a previous attempt (e.g. 409). */
  serverError?: string | null;
}

/**
 * Reschedule dialog for ADMIN / RECEPTIONIST.
 *
 * - Uses SlotPicker for the SAME doctor.
 * - Allows an optional new branch (defaults to the current one).
 * - Optional reason field.
 * - Passes the slot instants through unchanged.
 * - On 409 the parent clears serverError and slot; the child resets selection.
 */
export const RescheduleDialog: React.FC<RescheduleDialogProps> = ({
  isOpen,
  isLoading,
  doctorId,
  currentBranchId,
  onConfirm,
  onClose,
  serverError,
}) => {
  const today = toIstDateString(new Date());
  const [branchId, setBranchId] = useState<number>(currentBranchId);
  const [date, setDate] = useState('');
  const [slot, setSlot] = useState<AvailableSlot | null>(null);
  const [reason, setReason] = useState('');

  const branchesQuery = useQuery({
    queryKey: ['reschedule-branches'],
    queryFn: () => listBranches({ size: 100 }),
    enabled: isOpen,
  });

  // Reset state when dialog opens
  React.useEffect(() => {
    if (isOpen) {
      setBranchId(currentBranchId);
      setDate('');
      setSlot(null);
      setReason('');
    }
  }, [isOpen, currentBranchId]);

  // If the parent signals a 409 (serverError changes while open), clear the slot
  React.useEffect(() => {
    if (serverError) {
      setSlot(null);
    }
  }, [serverError]);

  const handleSelect = (s: AvailableSlot) => {
    setSlot(s);
  };

  const handleConfirm = () => {
    if (!slot) return;
    onConfirm({
      newStartTime: slot.startTime,
      newEndTime: slot.endTime,
      newBranchId: branchId !== currentBranchId ? branchId : undefined,
      reason: reason.trim() || undefined,
    });
  };

  const branchOptions = (branchesQuery.data?.content ?? []).map((b) => ({
    value: b.id,
    label: b.name,
  }));

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Reschedule appointment">
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {branchOptions.length > 0 && (
          <Select
            label="Branch"
            options={branchOptions}
            value={branchId}
            onChange={(e) => {
              setBranchId(Number(e.target.value));
              setDate('');
              setSlot(null);
            }}
          />
        )}

        <Input
          label="Date (IST)"
          type="date"
          value={date}
          min={today}
          onChange={(e) => {
            setDate(e.target.value);
            setSlot(null);
          }}
        />

        {date && (
          <div>
            <p style={{ margin: '0 0 8px', fontSize: '13px', color: '#64748b', fontWeight: 600 }}>
              Available slots
            </p>
            <SlotPicker
              doctorId={doctorId}
              branchId={branchId}
              date={date}
              selected={slot}
              onSelect={handleSelect}
            />
          </div>
        )}

        {serverError && (
          <p role="alert" style={{ margin: 0, color: '#ef4444', fontSize: '13px' }}>
            {serverError}
          </p>
        )}

        {slot && (
          <div style={{ fontSize: '13px', color: '#334155', padding: '8px', background: '#f0fdf9', borderRadius: '6px' }}>
            Selected: <strong>{formatIstDateTime(slot.startTime)}</strong>
          </div>
        )}

        <Input
          label="Reason (optional)"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Reason for rescheduling"
        />

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
          <Button type="button" variant="outline" size="md" onClick={onClose} disabled={isLoading}>
            Close
          </Button>
          <Button
            type="button"
            variant="primary"
            size="md"
            disabled={!slot || isLoading}
            onClick={handleConfirm}
          >
            {isLoading ? 'Rescheduling…' : 'Confirm reschedule'}
          </Button>
        </div>
      </div>
    </Modal>
  );
};

