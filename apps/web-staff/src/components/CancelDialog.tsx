import React, { useState } from 'react';
import { Modal, Button, Input } from '@hms/ui';

export interface CancelDialogProps {
  isOpen: boolean;
  isLoading: boolean;
  onConfirm: (reason: string) => void;
  onClose: () => void;
}

/**
 * Modal that requires a non-blank cancellation reason before enabling the
 * "Cancel appointment" button.
 */
export const CancelDialog: React.FC<CancelDialogProps> = ({
  isOpen,
  isLoading,
  onConfirm,
  onClose,
}) => {
  const [reason, setReason] = useState('');
  const trimmed = reason.trim();
  const empty = trimmed.length === 0;

  // Reset reason when dialog opens/closes
  React.useEffect(() => {
    if (!isOpen) setReason('');
  }, [isOpen]);

  const handleConfirm = () => {
    if (!empty) onConfirm(trimmed);
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Cancel appointment">
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <Input
          label="Reason (required)"
          id="cancel-reason"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Enter cancellation reason"
          aria-required="true"
          disabled={isLoading}
        />
        {empty && (
          <p role="alert" style={{ margin: 0, color: '#ef4444', fontSize: '13px' }}>
            Reason cannot be blank.
          </p>
        )}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
          <Button type="button" variant="outline" size="md" onClick={onClose} disabled={isLoading}>
            Keep appointment
          </Button>
          <Button
            type="button"
            variant="danger"
            size="md"
            disabled={empty || isLoading}
            onClick={handleConfirm}
          >
            {isLoading ? 'Cancelling…' : 'Cancel appointment'}
          </Button>
        </div>
      </div>
    </Modal>
  );
};

