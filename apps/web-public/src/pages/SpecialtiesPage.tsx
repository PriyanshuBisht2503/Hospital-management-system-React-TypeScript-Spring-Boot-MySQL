import React from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Card } from '@hms/ui';
import { publicApi } from '../lib/api';
import { useDocumentTitle } from '../hooks/useDocumentTitle';

export const SpecialtiesPage: React.FC = () => {
  useDocumentTitle('Specialties');

  const query = useQuery({ queryKey: ['specialties', 'list'], queryFn: () => publicApi.listSpecialties({ size: 100 }) });

  return (
    <div>
      <h1 style={{ margin: '0 0 6px', fontSize: '30px', color: '#0f172a' }}>Specialties</h1>
      <p style={{ margin: '0 0 24px', color: '#64748b' }}>Browse the specialties offered at CareHMS.</p>

      {query.isLoading ? (
        <p role="status">Loading specialties…</p>
      ) : query.isError ? (
        <p role="alert" style={{ color: '#b91c1c' }}>Could not load specialties. Please try again later.</p>
      ) : (query.data?.content ?? []).length === 0 ? (
        <p>No specialties available yet.</p>
      ) : (
        <div className="card-grid">
          {query.data!.content.map((specialty) => (
            <Card key={specialty.id} style={{ padding: '18px' }}>
              <h2 style={{ margin: '0 0 6px', fontSize: '17px', color: '#0f172a' }}>{specialty.name}</h2>
              <p style={{ margin: '0 0 12px', fontSize: '13px', color: '#64748b' }}>{specialty.description ?? 'Specialist care'}</p>
              <Link to={`/doctors?specialtyId=${specialty.id}`} style={{ color: '#0d9488', fontWeight: 600, fontSize: '13px' }}>
                See doctors →
              </Link>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
};
