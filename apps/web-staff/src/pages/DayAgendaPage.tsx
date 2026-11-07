import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { listAppointments } from '../api/appointments';
import { searchDoctors, getDoctorSlots } from '../api/doctors';
import { DoctorDayTimeline, DaySummaryStrip } from '../components/DoctorDayTimeline';
import { Button, Card, Input, Select } from '@hms/ui';
import { addIstDays, endOfIstDayUtc, formatIstTime, startOfIstDayUtc, toIstDateString } from '../lib/time';
import { ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useAuth } from '../context/AuthContext';

export const DayAgendaPage: React.FC = () => {
  useDocumentTitle('Day Agenda');
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const today = toIstDateString(new Date());
  const date = searchParams.get('date') ?? today;
  const doctorId = searchParams.get('doctorId') ? Number(searchParams.get('doctorId')) : undefined;
  const branchId = searchParams.get('branchId') ? Number(searchParams.get('branchId')) : undefined;
  const [hideCancelled, setHideCancelled] = useState(false);

  const setParam = (key: string, value: string | undefined) => {
    const next = new URLSearchParams(searchParams);
    if (value) next.set(key, value);
    else next.delete(key);
    setSearchParams(next);
  };

  const doctorsQuery = useQuery({ queryKey: ['agenda-doctors'], queryFn: () => searchDoctors({ size: 100 }) });
  const selectedDoctor = doctorsQuery.data?.content.find((d) => d.id === doctorId) ?? null;

  const appointmentsQuery = useQuery({
    queryKey: ['day-agenda', date, doctorId, branchId],
    queryFn: () => listAppointments({ doctorId, branchId, from: startOfIstDayUtc(date), to: endOfIstDayUtc(date), size: 100 }),
    enabled: !!doctorId,
  });

  const slotsQuery = useQuery({
    queryKey: ['agenda-slots', doctorId, branchId, date],
    queryFn: () => getDoctorSlots(doctorId!, { branchId: branchId!, date }),
    enabled: !!doctorId && !!branchId,
  });

  const appointments = appointmentsQuery.data?.content ?? [];
  const total = appointmentsQuery.data?.totalElements ?? 0;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', flexWrap: 'wrap', marginBottom: '12px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>Day Agenda</h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>Appointments and open slots on {date} (IST)</p>
        </div>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
          <Button variant="outline" size="sm" onClick={() => setParam('date', addIstDays(date, -1))} aria-label="Previous day"><ChevronLeft size={16} /></Button>
          <Button variant="outline" size="sm" onClick={() => setParam('date', today)}>Today</Button>
          <Button variant="outline" size="sm" onClick={() => setParam('date', addIstDays(date, 1))} aria-label="Next day"><ChevronRight size={16} /></Button>
          <Button variant="outline" size="sm" onClick={() => appointmentsQuery.refetch()} aria-label="Refresh"><RefreshCw size={16} /></Button>
        </div>
      </div>

      <Card style={{ padding: '16px', marginBottom: '20px' }}>
        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <div style={{ width: '220px' }}>
            <Select
              label="Doctor"
              placeholder="Select doctor"
              options={(doctorsQuery.data?.content ?? []).map((d) => ({ value: d.id, label: d.fullName }))}
              value={doctorId ?? ''}
              onChange={(e) => {
                setSearchParams(new URLSearchParams({ date, doctorId: e.target.value }));
              }}
            />
          </div>
          <div style={{ width: '200px' }}>
            <Select
              label="Branch (optional)"
              placeholder="All branches"
              options={(selectedDoctor?.branches ?? []).map((b) => ({ value: b.id, label: b.name }))}
              value={branchId ?? ''}
              onChange={(e) => setParam('branchId', e.target.value || undefined)}
            />
          </div>
          <div style={{ width: '180px' }}>
            <Input label="Date (IST)" type="date" value={date} onChange={(e) => setParam('date', e.target.value)} />
          </div>
          <label style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: '#334155' }}>
            <input type="checkbox" checked={hideCancelled} onChange={(e) => setHideCancelled(e.target.checked)} />
            Hide cancelled / rescheduled
          </label>
        </div>
      </Card>

      {!doctorId ? (
        <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>Select a doctor to view the day agenda.</div>
      ) : appointmentsQuery.isLoading ? (
        <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>Loading appointments...</div>
      ) : appointmentsQuery.isError ? (
        <div style={{ padding: '48px', textAlign: 'center', color: '#ef4444' }}>
          <p>Failed to load appointments: {(appointmentsQuery.error as Error)?.message}</p>
          <Button variant="outline" size="sm" onClick={() => appointmentsQuery.refetch()}>Retry</Button>
        </div>
      ) : (
        <>
          {total > 100 && (
            <div role="status" style={{ padding: '12px 16px', borderRadius: '8px', backgroundColor: '#fffbeb', border: '1px solid #fcd34d', color: '#92400e', fontSize: '13px', marginBottom: '16px' }}>
              Showing the first 100 of {total} appointments for this day. Narrow by branch to see fewer.
            </div>
          )}
          <div style={{ marginBottom: '20px' }}>
            <DaySummaryStrip appointments={appointments} />
          </div>
          <DoctorDayTimeline
            appointments={appointments}
            role={user?.role ?? 'RECEPTIONIST'}
            now={new Date()}
            hideCancelled={hideCancelled}
            queryKey={['day-agenda', date, doctorId, branchId]}
          />

          <Card style={{ padding: '16px', marginTop: '20px' }}>
            <h2 style={{ fontSize: '16px', fontWeight: 700, color: '#0f172a', margin: '0 0 12px' }}>Open slots</h2>
            {!branchId ? (
              <div style={{ color: '#64748b', fontSize: '14px' }}>Select a branch to see open slots.</div>
            ) : slotsQuery.isLoading ? (
              <div style={{ color: '#64748b', fontSize: '14px' }}>Loading slots...</div>
            ) : slotsQuery.isError ? (
              <div style={{ color: '#ef4444', fontSize: '14px' }}>Failed to load slots.</div>
            ) : (slotsQuery.data ?? []).length === 0 ? (
              <div style={{ color: '#64748b', fontSize: '14px' }}>No open slots for this date.</div>
            ) : (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                {slotsQuery.data!.map((slot) => (
                  <div key={slot.startTime} style={{ display: 'flex', gap: '8px', alignItems: 'center', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '8px 12px', fontSize: '13px' }}>
                    <span>{formatIstTime(slot.startTime)} – {formatIstTime(slot.endTime)}</span>
                    <Link to={`/appointments/new?doctorId=${doctorId}&branchId=${branchId}&date=${date}`}>Book</Link>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </>
      )}
    </div>
  );
};
