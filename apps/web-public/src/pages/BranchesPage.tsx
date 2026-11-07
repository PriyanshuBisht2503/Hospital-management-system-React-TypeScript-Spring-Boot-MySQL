import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Card } from '@hms/ui';
import { publicApi } from '../lib/api';
import { useDocumentTitle } from '../hooks/useDocumentTitle';

export const BranchesPage: React.FC = () => {
  useDocumentTitle('Branches');

  const query = useQuery({ queryKey: ['branches', 'list'], queryFn: () => publicApi.listBranches({ size: 100 }) });

  return (
    <div>
      <h1 style={{ margin: '0 0 6px', fontSize: '30px', color: '#0f172a' }}>Branches</h1>
      <p style={{ margin: '0 0 24px', color: '#64748b' }}>Our fictional hospital locations.</p>

      {query.isLoading ? (
        <p role="status">Loading branches…</p>
      ) : query.isError ? (
        <p role="alert" style={{ color: '#b91c1c' }}>Could not load branches. Please try again later.</p>
      ) : (query.data?.content ?? []).length === 0 ? (
        <p>No branches available yet.</p>
      ) : (
        <div className="card-grid">
          {query.data!.content.map((branch) => (
            <Card key={branch.id} style={{ padding: '18px' }}>
              <h2 style={{ margin: '0 0 6px', fontSize: '17px', color: '#0f172a' }}>{branch.name}</h2>
              <p style={{ margin: '0 0 6px', fontSize: '13px', color: '#64748b' }}>{[branch.address, branch.city].filter(Boolean).join(', ')}</p>
              {branch.phone && <p style={{ margin: '0 0 4px', fontSize: '13px', color: '#334155' }}>Phone: {branch.phone}</p>}
              {branch.email && <p style={{ margin: 0, fontSize: '13px', color: '#334155' }}>Email: {branch.email}</p>}
            </Card>
          ))}
        </div>
      )}
    </div>
  );
};
