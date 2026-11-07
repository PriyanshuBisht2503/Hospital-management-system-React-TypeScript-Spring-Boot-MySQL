import React from 'react';
import { Link } from 'react-router-dom';
import { Card } from '@hms/ui';
import { buildPatientAppUrl } from '../lib/patientAppUrl';
import { formatIstDateTime } from '../lib/time';
import type { Doctor, DoctorSearch } from '../lib/types';

export function formatFee(fee: number): string {
  return `AED ${Number(fee).toFixed(2)}`;
}

export function initials(fullName: string): string {
  return fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}

export const DoctorCard: React.FC<{ doctor: DoctorSearch | Doctor }> = ({ doctor }) => {
  const specialtyNames = (doctor.specialties ?? []).map((s) => s.name).join(', ') || 'General medicine';
  const branchNames = (doctor.branches ?? []).map((b) => b.name).join(', ') || '—';
  const nextAvailable = 'nextAvailableSlot' in doctor ? doctor.nextAvailableSlot : null;
  const primaryBranchId = doctor.branches?.[0]?.id;

  return (
    <Card style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div style={{ display: 'flex', gap: '14px', alignItems: 'center' }}>
        {doctor.photoUrl ? (
          <img
            src={doctor.photoUrl}
            alt={`Portrait of ${doctor.fullName}`}
            style={{ width: '56px', height: '56px', borderRadius: '50%', objectFit: 'cover' }}
          />
        ) : (
          <span aria-hidden="true" style={{ width: '56px', height: '56px', borderRadius: '50%', backgroundColor: '#e0f2fe', color: '#0369a1', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700 }}>
            {initials(doctor.fullName)}
          </span>
        )}
        <div>
          <h3 style={{ margin: 0, fontSize: '17px', color: '#0f172a' }}>{doctor.fullName}</h3>
          <p style={{ margin: '2px 0 0', fontSize: '13px', color: '#64748b' }}>
            {specialtyNames} · {doctor.experienceYears} yrs experience
          </p>
        </div>
      </div>

      <p style={{ margin: 0, fontSize: '13px', color: '#334155' }}>Fee: {formatFee(doctor.consultationFee)}</p>
      <p style={{ margin: 0, fontSize: '13px', color: '#334155' }}>Branches: {branchNames}</p>
      <p style={{ margin: 0, fontSize: '13px', color: nextAvailable ? '#0d9488' : '#94a3b8' }}>
        {nextAvailable ? `Next available: ${formatIstDateTime(nextAvailable)}` : 'No slots soon'}
      </p>

      <div style={{ display: 'flex', gap: '10px', marginTop: '6px', flexWrap: 'wrap' }}>
        <Link
          to={`/doctors/${doctor.id}`}
          style={{ padding: '8px 14px', borderRadius: '8px', border: '1.5px solid #cbd5e1', color: '#334155', textDecoration: 'none', fontWeight: 600, fontSize: '13px' }}
        >
          View profile
        </Link>
        <a
          href={buildPatientAppUrl('/book', { doctorId: doctor.id, branchId: primaryBranchId })}
          style={{ padding: '8px 14px', borderRadius: '8px', backgroundColor: '#0d9488', color: '#fff', textDecoration: 'none', fontWeight: 600, fontSize: '13px' }}
        >
          Book appointment
        </a>
      </div>
    </Card>
  );
};
