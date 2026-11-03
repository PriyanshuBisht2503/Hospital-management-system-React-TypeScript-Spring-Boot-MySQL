import React, { useState, useEffect } from 'react';
import { useQueryClient, useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useToast, Button } from '@hms/ui';
import {
  confirmAppointment,
  checkInAppointment,
  startConsultation,
  completeAppointment,
  markNoShow,
  cancelAppointment,
  rescheduleAppointment,
} from '../api/appointments';
import {
  getAllowedActions,
  getActionDisabledState,
  type AppointmentActionKey,
} from '../lib/appointmentStatus';
import { CancelDialog } from './CancelDialog';
import { RescheduleDialog } from './RescheduleDialog';
import { ApiError } from '../types/auth';
import type { Appointment, RescheduleAppointmentRequest } from '../types/appointment';
import type { Role } from '../types/auth';

export interface AppointmentActionsProps {
  appointment: Appointment;
  role: Role;
  /** Injectable "now" for time-hint logic — never read clock inside the component. */
  now: Date;
  /** React-Query key to invalidate / refetch on mutation success or 409. */
  queryKey: unknown[];
  /**
   * When the parent wants to open the reschedule dialog externally
   * (e.g. the "Needs rescheduling" banner button), set this to true.
   */
  externalRescheduleOpen?: boolean;
  onExternalRescheduleClose?: () => void;
}

/**
 * Renders the permitted action buttons for the given appointment status + role.
 * All buttons are disabled while ANY mutation is in-flight (no double-submit).
 * Only ApiError is handled; anything else is rethrown so it surfaces as an
 * unhandled rejection for the test / error boundary to catch.
 */
export const AppointmentActions: React.FC<AppointmentActionsProps> = ({
  appointment,
  role,
  now,
  queryKey,
  externalRescheduleOpen = false,
  onExternalRescheduleClose,
}) => {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { toast } = useToast();

  const [cancelOpen, setCancelOpen] = useState(false);
  const [rescheduleOpen, setRescheduleOpen] = useState(false);
  const [rescheduleError, setRescheduleError] = useState<string | null>(null);
  const [inflightKey, setInflightKey] = useState<AppointmentActionKey | null>(null);

  // Sync external open signal
  useEffect(() => {
    if (externalRescheduleOpen) {
      setRescheduleOpen(true);
    }
  }, [externalRescheduleOpen]);

  const closeReschedule = () => {
    setRescheduleOpen(false);
    setRescheduleError(null);
    onExternalRescheduleClose?.();
  };

  const mutation = useMutation({
    mutationFn: async ({
      key,
      cancelReason,
      rescheduleReq,
    }: {
      key: AppointmentActionKey;
      cancelReason?: string;
      rescheduleReq?: RescheduleAppointmentRequest;
    }) => {
      const id = appointment.id;
      switch (key) {
        case 'CONFIRM':
          return confirmAppointment(id);
        case 'CHECK_IN':
          return checkInAppointment(id);
        case 'START_CONSULTATION':
          return startConsultation(id);
        case 'COMPLETE':
          return completeAppointment(id);
        case 'NO_SHOW':
          return markNoShow(id);
        case 'CANCEL':
          return cancelAppointment(id, { reason: cancelReason! });
        case 'RESCHEDULE':
          return rescheduleAppointment(id, rescheduleReq!);
        default:
          throw new Error(`Unknown action key: ${key}`);
      }
    },
    onSuccess: (updated, { key }) => {
      setInflightKey(null);
      queryClient.invalidateQueries({ queryKey });
      queryClient.invalidateQueries({ queryKey: ['appointments'] });

      if (key === 'RESCHEDULE') {
        closeReschedule();
        toast.success('Appointment rescheduled');
        navigate(`/appointments/${(updated as Appointment).id}`);
      } else if (key === 'CANCEL') {
        setCancelOpen(false);
        toast.success('Appointment cancelled');
      } else {
        toast.success('Appointment updated');
      }
    },
    onError: (err, { key }) => {
      setInflightKey(null);
      if (!(err instanceof ApiError)) {
        throw err; // non-ApiError → rethrow
      }
      // Refetch so status is never stale after a 409
      queryClient.invalidateQueries({ queryKey });

      if (key === 'RESCHEDULE') {
        if (err.status === 409) {
          // Slot was just taken — refetch the slots so the dialog shows fresh availability.
          queryClient.invalidateQueries({ queryKey: ['doctor-slots'] });
        }
        // Keep dialog open; clear slot selection via serverError prop
        setRescheduleError(err.message);
        return;
      }
      toast.error(err.message);
    },
  });

  const isAnyPending = mutation.isPending;
  const allowedActions = getAllowedActions(appointment.status, role);

  const fire = (key: AppointmentActionKey) => {
    if (isAnyPending) return;
    setInflightKey(key);
    if (key === 'CANCEL') {
      setCancelOpen(true);
      return;
    }
    if (key === 'RESCHEDULE') {
      setRescheduleOpen(true);
      return;
    }
    mutation.mutate({ key });
  };

  // Render nothing for terminal statuses (no allowed actions)
  if (allowedActions.length === 0 && !cancelOpen && !rescheduleOpen) {
    return null;
  }

  return (
    <>
      {allowedActions.length > 0 && (
        <div
          style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}
          aria-label="Appointment actions"
        >
          {allowedActions.map((action) => {
            const { disabled: timeDisabled, tooltip } = getActionDisabledState(
              action,
              appointment.startTime,
              now,
            );
            const isThisInFlight = inflightKey === action.key && isAnyPending;
            const disabled = isAnyPending || timeDisabled;

            return (
              <button
                key={action.key}
                type="button"
                disabled={disabled}
                title={tooltip ?? undefined}
                aria-label={isThisInFlight ? `${action.label} in progress` : action.label}
                data-action-key={action.key}
                onClick={() => fire(action.key)}
                style={{
                  padding: '8px 16px',
                  borderRadius: '8px',
                  border: '1.5px solid #cbd5e1',
                  backgroundColor: disabled ? '#f8fafc' : '#ffffff',
                  color: disabled ? '#94a3b8' : '#334155',
                  cursor: disabled ? 'not-allowed' : 'pointer',
                  fontSize: '13px',
                  fontWeight: 600,
                }}
              >
                {isThisInFlight ? `${action.label}…` : action.label}
              </button>
            );
          })}
        </div>
      )}

      <CancelDialog
        isOpen={cancelOpen}
        isLoading={inflightKey === 'CANCEL' && isAnyPending}
        onConfirm={(reason) => mutation.mutate({ key: 'CANCEL', cancelReason: reason })}
        onClose={() => {
          if (!isAnyPending) setCancelOpen(false);
        }}
      />

      {appointment.doctorId != null && (
        <RescheduleDialog
          isOpen={rescheduleOpen}
          isLoading={inflightKey === 'RESCHEDULE' && isAnyPending}
          doctorId={appointment.doctorId}
          currentBranchId={appointment.branchId ?? 0}
          onConfirm={(req) => {
            setRescheduleError(null);
            mutation.mutate({ key: 'RESCHEDULE', rescheduleReq: req });
          }}
          onClose={() => {
            if (!isAnyPending) closeReschedule();
          }}
          serverError={rescheduleError}
        />
      )}
    </>
  );
};
