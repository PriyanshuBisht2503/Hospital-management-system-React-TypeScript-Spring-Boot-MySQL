import React, { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQueries, useQuery } from '@tanstack/react-query';
import { Card, Select } from '@hms/ui';
import { ApiError, publicApi } from '../lib/api';
import { buildPatientAppUrl } from '../lib/patientAppUrl';
import { formatIstDateTime, formatIstTime, nextIstDates } from '../lib/time';
import { formatFee } from '../components/DoctorCard';
import { useDocumentTitle } from '../hooks/useDocumentTitle';

const DoctorNotFound: React.FC = () => (
  <div style={{ textAlign: 'center', padding: '48px 0' }}>
    <h1 style={{ margin: '0 0 10px', fontSize: '26px', color: '#0f172a' }}>Doctor not found</h1>
    <p style={{ margin: '0 0 20px', color: '#64748b' }}>This doctor does not exist or is no longer available.</p>
    <Link to="/doctors" style={{ color: '#0d9488', fontWeight: 600 }}>Back to doctors</Link>
  </div>
);

export const DoctorProfilePage: React.FC = () => {
  useDocumentTitle('Doctor profile');
  const { id } = useParams<{ id: string }>();
  const doctorId = Number(id);
  const validId = Number.isInteger(doctorId) && doctorId > 0;

  const doctorQuery = useQuery({
    queryKey: ['doctor', doctorId],
    queryFn: () => publicApi.getDoctor(doctorId),
    enabled: validId,
  });

  const [branchId, setBranchId] = useState<number | null>(null);
  useEffect(() => {
    if (doctorQuery.data && branchId == null) {
      setBranchId(doctorQuery.data.branches?.[0]?.id ?? null);
    }
  }, [doctorQuery.data, branchId]);

  const dates = useMemo(() => nextIstDates(7), []);

  const slotQueries = useQueries({
    queries: dates.map((date) => ({
      queryKey: ['slots', doctorId, branchId, date],
      queryFn: () => publicApi.getDoctorSlots(doctorId, { branchId: branchId ?? undefined, date }),
      enabled: branchId != null,
    })),
  });

  if (!validId) return <DoctorNotFound />;
  if (doctorQuery.isLoading) return <p role="status">Loading doctor…</p>;
  if (doctorQuery.isError) {
    if (doctorQuery.error instanceof ApiError && doctorQuery.error.status === 404) return <DoctorNotFound />;
    return <p role="alert" style={{ color: '#b91c1c' }}>Could not load this doctor.</p>;
  }

  const doctor = doctorQuery.data!;
  const bookHref = buildPatientAppUrl('/book', { doctorId: doctor.id, branchId: branchId ?? undefined });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <Card style={{ padding: '24px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: '16px', flexWrap: 'wrap' }}>
          <div>
            <h1 style={{ margin: '0 0 6px', fontSize: '28px', color: '#0f172a' }}>{doctor.fullName}</h1>
            <p style={{ margin: '0 0 6px', color: '#64748b' }}>
              {(doctor.specialties ?? []).map((s) => s.name).join(', ') || 'General medicine'}
            </p>
            <p style={{ margin: 0, color: '#334155', fontSize: '14px' }}>
              {doctor.experienceYears} years experience · Fee: {formatFee(doctor.consultationFee)}
            </p>
            {doctor.qualifications && <p style={{ margin: '6px 0 0', color: '#334155', fontSize: '14px' }}>{doctor.qualifications}</p>}
            {doctor.languages && <p style={{ margin: '4px 0 0', color: '#334155', fontSize: '14px' }}>Languages: {doctor.languages}</p>}
          </div>
          <a href={bookHref} style={{ alignSelf: 'flex-start', backgroundColor: '#0d9488', color: '#fff', padding: '10px 18px', borderRadius: '8px', textDecoration: 'none', fontWeight: 700 }}>
            Book appointment
          </a>
        </div>

        {doctor.bio && <p style={{ margin: '16px 0 0', color: '#334155' }}>{doctor.bio}</p>}
      </Card>

      <Card style={{ padding: '24px' }}>
        <h2 style={{ margin: '0 0 12px', fontSize: '18px', color: '#0f172a' }}>Availability</h2>

        {(doctor.branches ?? []).length > 1 && (
          <div style={{ maxWidth: '280px', marginBottom: '16px' }}>
            <Select
              label="Branch"
              options={doctor.branches.map((b) => ({ value: b.id, label: b.name }))}
              value={branchId ?? ''}
              onChange={(e) => setBranchId(e.target.value ? Number(e.target.value) : null)}
            />
          </div>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: '12px' }}>
          {dates.map((date, index) => {
            const slots = slotQueries[index]?.data ?? [];
            return (
              <div key={date} style={{ border: '1px solid #e2e8f0', borderRadius: '10px', padding: '12px' }}>
                <h3 style={{ margin: '0 0 8px', fontSize: '14px', color: '#0f172a' }}>{date}</h3>
                {slotQueries[index]?.isLoading ? (
                  <p style={{ margin: 0, fontSize: '12px', color: '#94a3b8' }}>Loading…</p>
                ) : slots.length === 0 ? (
                  <p style={{ margin: 0, fontSize: '12px', color: '#94a3b8' }}>No slots</p>
                ) : (
                  <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                    {slots.map((slot) => (
                      <li key={slot.startTime}>
                        <span
                          title={`${formatIstDateTime(slot.startTime)} – ${formatIstDateTime(slot.endTime)}`}
                          style={{ display: 'inline-block', backgroundColor: '#f0fdfa', color: '#0f172a', borderRadius: '999px', padding: '3px 10px', fontSize: '12px' }}
                        >
                          {formatIstTime(slot.startTime)}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            );
          })}
        </div>

        <p style={{ margin: '16px 0 0', fontSize: '13px', color: '#64748b' }}>
          Times are shown in IST. Sign in to the patient portal to book.
        </p>
      </Card>
    </div>
  );
};
