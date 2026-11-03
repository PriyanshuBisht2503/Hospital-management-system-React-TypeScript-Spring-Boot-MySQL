import React from 'react';
import { Card } from '@hms/ui';

/** Shown for any patient 404 (nonexistent or inaccessible look identical, matching backend cloaking). */
export const PatientNotFound: React.FC = () => (
  <Card style={{ maxWidth: '640px', margin: '0 auto', padding: '48px', textAlign: 'center' }}>
    <h1 style={{ fontSize: '20px', fontWeight: 700, color: '#0f172a', margin: '0 0 8px' }}>
      Patient not found
    </h1>
    <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>
      This patient record does not exist or you do not have access to it.
    </p>
  </Card>
);
