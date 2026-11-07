import React, { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Input, Select } from '@hms/ui';
import { publicApi } from '../lib/api';
import { buildPatientAppUrl } from '../lib/patientAppUrl';
import { useDocumentTitle } from '../hooks/useDocumentTitle';

export const HomePage: React.FC = () => {
  useDocumentTitle('Home');
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [specialtyId, setSpecialtyId] = useState<string>('');

  const specialtiesQuery = useQuery({
    queryKey: ['specialties', 'home'],
    queryFn: () => publicApi.listSpecialties({ size: 100 }),
  });

  const specialties = useMemo(() => (specialtiesQuery.data?.content ?? []).slice(0, 8), [specialtiesQuery.data]);

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    const params = new URLSearchParams();
    if (search.trim()) params.set('search', search.trim());
    if (specialtyId) params.set('specialtyId', specialtyId);
    navigate(`/doctors${params.toString() ? `?${params.toString()}` : ''}`);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '48px' }}>
      <section aria-labelledby="hero-heading" style={{ backgroundColor: '#0f172a', color: '#fff', borderRadius: '20px', padding: '40px 28px' }}>
        <p style={{ margin: '0 0 8px', color: '#5eead4', fontWeight: 700, fontSize: '13px', letterSpacing: '0.08em' }}>DEMO HOSPITAL</p>
        <h1 id="hero-heading" style={{ margin: '0 0 12px', fontSize: '36px', lineHeight: 1.15 }}>Find a doctor and book your visit</h1>
        <p style={{ margin: '0 0 24px', color: '#cbd5e1', maxWidth: '640px' }}>
          CareHMS is a fictional hospital used for a learning project. Browse specialties and doctors, then continue to the patient portal to book a real slot.
        </p>

        <form onSubmit={handleSubmit} aria-label="Find a doctor" style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'flex-end', maxWidth: '760px' }}>
          <div style={{ flex: 2, minWidth: '220px' }}>
            <Input label="Doctor name" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="e.g. Sarah" />
          </div>
          <div style={{ flex: 1, minWidth: '180px' }}>
            <Select
              label="Specialty"
              placeholder="Any specialty"
              options={specialties.map((s) => ({ value: s.id, label: s.name }))}
              value={specialtyId}
              onChange={(e) => setSpecialtyId(e.target.value)}
            />
          </div>
          <Button type="submit" variant="primary" size="md">Search doctors</Button>
        </form>
      </section>

      <section aria-labelledby="specialties-heading">
        <h2 id="specialties-heading" style={{ margin: '0 0 16px', fontSize: '24px', color: '#0f172a' }}>Specialties</h2>
        {specialtiesQuery.isLoading ? (
          <p role="status">Loading specialties…</p>
        ) : specialtiesQuery.isError ? (
          <p role="alert" style={{ color: '#b91c1c' }}>Could not load specialties. Please try again later.</p>
        ) : (
          <div className="card-grid">
            {specialties.map((specialty) => (
              <Card key={specialty.id} style={{ padding: '18px' }}>
                <h3 style={{ margin: '0 0 6px', fontSize: '16px', color: '#0f172a' }}>{specialty.name}</h3>
                <p style={{ margin: '0 0 12px', fontSize: '13px', color: '#64748b' }}>{specialty.description ?? 'Specialist care'}</p>
                <Link to={`/doctors?specialtyId=${specialty.id}`} style={{ color: '#0d9488', fontWeight: 600, fontSize: '13px' }}>
                  See doctors →
                </Link>
              </Card>
            ))}
          </div>
        )}
      </section>

      <section aria-labelledby="how-heading">
        <h2 id="how-heading" style={{ margin: '0 0 16px', fontSize: '24px', color: '#0f172a' }}>How it works</h2>
        <div className="card-grid">
          {[
            { title: '1. Find a doctor', body: 'Search by name or specialty and compare experience and fees.' },
            { title: '2. Pick a slot', body: 'Open the profile to see the next 7 days of available times in IST.' },
            { title: '3. Manage online', body: 'Sign in to the patient portal to book, reschedule or cancel.' },
          ].map((step) => (
            <Card key={step.title} style={{ padding: '18px' }}>
              <h3 style={{ margin: '0 0 6px', fontSize: '16px', color: '#0f172a' }}>{step.title}</h3>
              <p style={{ margin: 0, fontSize: '13px', color: '#64748b' }}>{step.body}</p>
            </Card>
          ))}
        </div>
      </section>

      <section aria-labelledby="branches-heading">
        <h2 id="branches-heading" style={{ margin: '0 0 16px', fontSize: '24px', color: '#0f172a' }}>Our branches</h2>
        <BranchesStrip />
      </section>

      <section aria-labelledby="cta-heading" style={{ textAlign: 'center', backgroundColor: '#f0fdfa', borderRadius: '20px', padding: '36px 24px' }}>
        <h2 id="cta-heading" style={{ margin: '0 0 10px', fontSize: '24px', color: '#0f172a' }}>Ready to book?</h2>
        <p style={{ margin: '0 0 20px', color: '#475569' }}>Continue to the patient portal where you can sign in with a one-time code.</p>
        <a href={buildPatientAppUrl('/book')} style={{ display: 'inline-block', backgroundColor: '#0d9488', color: '#fff', padding: '12px 22px', borderRadius: '10px', textDecoration: 'none', fontWeight: 700 }}>
          Book an appointment
        </a>
      </section>
    </div>
  );
};

const BranchesStrip: React.FC = () => {
  const branchesQuery = useQuery({ queryKey: ['branches', 'home'], queryFn: () => publicApi.listBranches({ size: 20 }) });

  if (branchesQuery.isLoading) return <p role="status">Loading branches…</p>;
  if (branchesQuery.isError) return <p role="alert" style={{ color: '#b91c1c' }}>Could not load branches.</p>;

  return (
    <div className="card-grid">
      {(branchesQuery.data?.content ?? []).map((branch) => (
        <Card key={branch.id} style={{ padding: '18px' }}>
          <h3 style={{ margin: '0 0 6px', fontSize: '16px', color: '#0f172a' }}>{branch.name}</h3>
          <p style={{ margin: 0, fontSize: '13px', color: '#64748b' }}>{[branch.address, branch.city].filter(Boolean).join(', ')}</p>
        </Card>
      ))}
    </div>
  );
};
