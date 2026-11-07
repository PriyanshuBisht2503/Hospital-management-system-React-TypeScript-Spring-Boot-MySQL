import React from 'react';
import { Link } from 'react-router-dom';
import { Card } from '@hms/ui';
import { buildPatientAppUrl } from '../lib/patientAppUrl';
import { useDocumentTitle } from '../hooks/useDocumentTitle';

export const AboutPage: React.FC = () => {
  useDocumentTitle('About');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <h1 style={{ margin: 0, fontSize: '30px', color: '#0f172a' }}>About CareHMS</h1>

      <Card style={{ padding: '20px' }}>
        <h2 style={{ margin: '0 0 10px', fontSize: '18px', color: '#0f172a' }}>A fictional hospital project</h2>
        <p style={{ margin: '0 0 10px', color: '#334155' }}>
          CareHMS is a learning project that models a hospital management system. All names, locations, doctors and
          appointments shown here are fictional and exist only to demonstrate the software.
        </p>
        <p style={{ margin: 0, color: '#334155' }}>
          There are four roles in the system: administrators, doctors, receptionists and patients. This public website
          is read-only; booking happens in the patient portal.
        </p>
      </Card>

      <Card style={{ padding: '20px' }}>
        <h2 style={{ margin: '0 0 10px', fontSize: '18px', color: '#0f172a' }}>Contact</h2>
        <p style={{ margin: '0 0 6px', color: '#334155' }}>Demo contact: hello@example.com</p>
        <p style={{ margin: 0, color: '#334155' }}>Demo address: 100 Medical City Way, Dubai</p>
      </Card>

      <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
        <a href={buildPatientAppUrl('/login')} style={{ backgroundColor: '#0d9488', color: '#fff', padding: '10px 18px', borderRadius: '8px', textDecoration: 'none', fontWeight: 600 }}>
          Patient login
        </a>
        <Link to="/doctors" style={{ padding: '10px 18px', borderRadius: '8px', border: '1.5px solid #cbd5e1', color: '#334155', textDecoration: 'none', fontWeight: 600 }}>
          Browse doctors
        </Link>
      </div>
    </div>
  );
};
