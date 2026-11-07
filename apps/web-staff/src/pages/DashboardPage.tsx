import React from 'react';
import { useAuth } from '../context/AuthContext';
import { Card, Badge, Button } from '@hms/ui';

export const DashboardPage: React.FC = () => {
  const { user, logout } = useAuth();

  if (!user) return null;

  const isStaffOnlyRole = user.role === 'DOCTOR' || user.role === 'RECEPTIONIST';

  return (
    <div style={{ padding: '32px', maxWidth: '1000px', margin: '0 auto', fontFamily: 'Inter, system-ui, sans-serif' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', margin: '0 0 6px' }}>
            Welcome, {user.fullName}
          </h1>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Badge variant="info">{user.role}</Badge>
            <span style={{ fontSize: '13px', color: '#64748b' }}>{user.email}</span>
          </div>
        </div>
        <Button variant="outline" size="sm" onClick={() => logout()}>
          Sign Out
        </Button>
      </div>

      {isStaffOnlyRole ? (
        <Card>
          <div style={{ padding: '16px', textAlign: 'center' }}>
            <h2 style={{ fontSize: '18px', fontWeight: 600, color: '#0f172a', marginBottom: '8px' }}>
              Welcome to the Staff Portal
            </h2>
            <p style={{ fontSize: '14px', color: '#64748b', maxWidth: '520px', margin: '0 auto', lineHeight: 1.6 }}>
              {user.role === 'DOCTOR'
                ? 'Doctor clinical workflows, consultation notes, and patient appointments are coming in Phase 2 / 5.'
                : 'Receptionist desk workflows, patient check-ins, and token queues are coming in Phase 2 / 5.'}
            </p>
            <div style={{ marginTop: '20px' }}>
              <Badge variant="neutral">Status: Account Active</Badge>
            </div>
          </div>
        </Card>
      ) : (
        <Card>
          <div style={{ padding: '16px' }}>
            <h2 style={{ fontSize: '18px', fontWeight: 600, color: '#0f172a', marginBottom: '8px' }}>
              Administrator Overview
            </h2>
            <p style={{ fontSize: '14px', color: '#64748b', margin: 0, lineHeight: 1.6 }}>
              Select a module from the admin navigation to manage Users, Master Data, Doctors, or view the Audit Log.
            </p>
          </div>
        </Card>
      )}
    </div>
  );
};

