import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { listAppointments } from '../api/appointments';
import { DoctorDayTimeline, DaySummaryStrip } from '../components/DoctorDayTimeline';
import { Button } from '@hms/ui';
import { addIstDays, endOfIstDayUtc, startOfIstDayUtc, toIstDateString } from '../lib/time';
import { ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react';
import { useDocumentTitle } from '../hooks/useDocumentTitle';

export const MyDayPage: React.FC = () => {
  useDocumentTitle('My Day');
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const today = toIstDateString(new Date());
  const date = searchParams.get('date') ?? today;
  const [hideCancelled, setHideCancelled] = useState(false);

  const setDate = (d: string) => {
    const next = new URLSearchParams(searchParams);
    next.set('date', d);
    setSearchParams(next);
  };

  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey: ['my-day', date],
    queryFn: () => listAppointments({ from: startOfIstDayUtc(date), to: endOfIstDayUtc(date), size: 100 }),
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
  });

  const appointments = data?.content ?? [];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', flexWrap: 'wrap', marginBottom: '12px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>My Day</h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>Appointments on {date} (IST)</p>
        </div>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
          <Button variant="outline" size="sm" onClick={() => setDate(addIstDays(date, -1))} aria-label="Previous day">
            <ChevronLeft size={16} />
          </Button>
          <Button variant="outline" size="sm" onClick={() => setDate(today)}>Today</Button>
          <Button variant="outline" size="sm" onClick={() => setDate(addIstDays(date, 1))} aria-label="Next day">
            <ChevronRight size={16} />
          </Button>
          <Button variant="outline" size="sm" onClick={() => refetch()} aria-label="Refresh">
            <RefreshCw size={16} />
          </Button>
        </div>
      </div>

      <label style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: '#334155', marginBottom: '16px' }}>
        <input type="checkbox" checked={hideCancelled} onChange={(e) => setHideCancelled(e.target.checked)} />
        Hide cancelled / rescheduled
      </label>

      {isLoading ? (
        <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>Loading appointments...</div>
      ) : isError ? (
        <div style={{ padding: '48px', textAlign: 'center', color: '#ef4444' }}>
          <p>Failed to load appointments: {(error as Error)?.message}</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>Retry</Button>
        </div>
      ) : (
        <>
          <div style={{ marginBottom: '20px' }}>
            <DaySummaryStrip appointments={appointments} />
          </div>
          {isFetching && <div style={{ fontSize: '12px', color: '#64748b', marginBottom: '8px' }}>Refreshing…</div>}
          <DoctorDayTimeline
            appointments={appointments}
            role={user?.role ?? 'DOCTOR'}
            now={new Date()}
            hideCancelled={hideCancelled}
            queryKey={['my-day', date]}
          />
        </>
      )}
    </div>
  );
};
