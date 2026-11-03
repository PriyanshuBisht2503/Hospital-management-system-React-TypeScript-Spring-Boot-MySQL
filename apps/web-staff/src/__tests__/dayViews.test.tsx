// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ToastProvider } from '@hms/ui';
import { MyDayPage } from '../pages/MyDayPage';
import { DayAgendaPage } from '../pages/DayAgendaPage';
import { useAuth } from '../context/AuthContext';
import { addIstDays, endOfIstDayUtc, istTimeToUtc, startOfIstDayUtc, toIstDateString } from '../lib/time';
import type { CurrentUser } from '../types/auth';
import type { Appointment } from '../types/appointment';

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }));
const mockUseAuth = vi.mocked(useAuth);

const DOCTOR: CurrentUser = { id: 3, email: 'doc@x', phone: null, role: 'DOCTOR', fullName: 'Doc', enabled: true, doctorId: 9 };
const RECEPTIONIST: CurrentUser = { id: 2, email: 'rec@x', phone: null, role: 'RECEPTIONIST', fullName: 'Rec', enabled: true, doctorId: null };

const authVal = (u: CurrentUser) => ({ user: u, isAuthenticated: true, isLoading: false, authError: null, login: async () => {}, logout: async () => {}, clearError: () => {} });
const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { 'Content-Type': 'application/json' } });
const emptyPage = { content: [], pageNumber: 0, pageSize: 100, totalElements: 0, totalPages: 0, isFirst: true, isLast: true };

const today = toIstDateString(new Date());

const doctor = { id: 9, fullName: 'Dr. Nine', branches: [{ id: 1, name: 'Main', code: 'M', city: 'Dubai', isActive: true }], specialties: [], isActive: true, consultationFee: 100, nextAvailableSlot: null };

function appt(id: number, start: string, end: string, status: Appointment['status'], patient: string, needsRescheduling = false): Appointment {
  return {
    id, patient: { id, fullName: patient, age: 30, phone: '+971501234567', email: null }, doctorId: 9, doctorName: 'Dr. Nine', branchId: 1, branchName: 'Main',
    startTime: start, endTime: end, type: 'IN_PERSON', status, reasonForVisit: 'Check-up', cancelReason: null, cancelledBy: null,
    createdBy: 'admin', rescheduledFromId: null, needsRescheduling, confirmedAt: null, checkedInAt: null, startedAt: null, completedAt: null, cancelledAt: null, createdAt: '2026-01-01T00:00:00Z',
  };
}

function renderPage(user: CurrentUser, path: string, entry: string, element: React.ReactElement) {
  mockUseAuth.mockReturnValue(authVal(user));
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <ToastProvider>
        <MemoryRouter initialEntries={[entry]}>
          <Routes>
            <Route path={path} element={element} />
            <Route path="/appointments/new" element={<div>WIZARD</div>} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

const renderMyDay = (entry = '/my-day') => renderPage(DOCTOR, '/my-day', entry, <MyDayPage />);
const renderDayAgenda = (entry: string) => renderPage(RECEPTIONIST, '/day-agenda', entry, <DayAgendaPage />);

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('my day timeline', () => {
  it('orders by IST start time and formats 00:00 and 23:30 ranges', async () => {
    const late = appt(1, istTimeToUtc(today, '23:30'), endOfIstDayUtc(today), 'COMPLETED', 'Late Patient');
    const early = appt(2, startOfIstDayUtc(today), istTimeToUtc(today, '00:30'), 'CONFIRMED', 'Early Patient');
    const mid = appt(3, istTimeToUtc(today, '10:00'), istTimeToUtc(today, '10:30'), 'CHECKED_IN', 'Mid Patient');

    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/appointments')) return json({ ...emptyPage, content: [late, early, mid], totalElements: 3, totalPages: 1 });
      return json(emptyPage);
    }));

    renderMyDay();
    await screen.findByText('Early Patient');

    const ranges = screen.getAllByText(/^\d\d:\d\d \u2013 \d\d:\d\d$/).map((el) => el.textContent);
    expect(ranges).toEqual(['00:00 – 00:30', '10:00 – 10:30', '23:30 – 00:00']);
  });

  it('summary strip counts each status bucket', async () => {
    const list = [
      appt(1, istTimeToUtc(today, '09:00'), istTimeToUtc(today, '09:30'), 'CHECKED_IN', 'Pat One'),
      appt(2, istTimeToUtc(today, '10:00'), istTimeToUtc(today, '10:30'), 'IN_CONSULTATION', 'Pat Two'),
      appt(3, istTimeToUtc(today, '11:00'), istTimeToUtc(today, '11:30'), 'COMPLETED', 'Pat Three'),
      appt(4, istTimeToUtc(today, '12:00'), istTimeToUtc(today, '12:30'), 'CANCELLED', 'Pat Four'),
      appt(5, istTimeToUtc(today, '13:00'), istTimeToUtc(today, '13:30'), 'NO_SHOW', 'Pat Five'),
    ];
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/appointments')) return json({ ...emptyPage, content: list, totalElements: 5, totalPages: 1 });
      return json(emptyPage);
    }));

    renderMyDay();
    await screen.findByText('Pat One');

    const summary = screen.getByRole('group', { name: 'Day summary' });
    const countFor = (label: string) => {
      const labelEl = within(summary).getByText(label);
      return within(labelEl.parentElement as HTMLElement).getByText(/^\d+$/).textContent;
    };
    expect(countFor('Total')).toBe('5');
    expect(countFor('Checked in')).toBe('1');
    expect(countFor('In consultation')).toBe('1');
    expect(countFor('Completed')).toBe('1');
    expect(countFor('Cancelled / no-show')).toBe('2');
  });

  it('hide-cancelled toggle removes cancelled rows', async () => {
    const confirmed = appt(1, istTimeToUtc(today, '09:00'), istTimeToUtc(today, '09:30'), 'CONFIRMED', 'Alice One');
    const cancelled = appt(2, istTimeToUtc(today, '10:00'), istTimeToUtc(today, '10:30'), 'CANCELLED', 'Bob Two');
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/appointments')) return json({ ...emptyPage, content: [confirmed, cancelled], totalElements: 2, totalPages: 1 });
      return json(emptyPage);
    }));

    renderMyDay();
    await screen.findByText('Alice One');
    expect(screen.getByText('Bob Two')).toBeTruthy();

    fireEvent.click(screen.getByLabelText(/Hide cancelled/i));

    expect(screen.queryByText('Bob Two')).toBeNull();
    expect(screen.getByText('Alice One')).toBeTruthy();
  });

  it('day navigation requests the correct IST day boundaries', async () => {
    const urls: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/appointments')) { urls.push(url); return json(emptyPage); }
      return json(emptyPage);
    }));

    renderMyDay();
    await waitFor(() => expect(urls.length).toBeGreaterThan(0));

    const first = new URLSearchParams(urls[0].split('?')[1] ?? '');
    expect(first.get('from')).toBe(startOfIstDayUtc(today));
    expect(first.get('to')).toBe(endOfIstDayUtc(today));

    fireEvent.click(screen.getByRole('button', { name: 'Next day' }));
    await waitFor(() => expect(urls.length).toBeGreaterThan(1));

    const tomorrow = addIstDays(today, 1);
    const last = new URLSearchParams(urls[urls.length - 1].split('?')[1] ?? '');
    expect(last.get('from')).toBe(startOfIstDayUtc(tomorrow));
    expect(last.get('to')).toBe(endOfIstDayUtc(tomorrow));
  });
});

describe('day agenda', () => {
  it('requests the selected doctor/date and lists open slots with a Book link', async () => {
    const urls: string[] = [];
    const slot = { doctorId: 9, branchId: 1, startTime: istTimeToUtc(today, '09:30'), endTime: istTimeToUtc(today, '10:00'), slotMinutes: 30 };
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/doctors/search')) return json({ ...emptyPage, content: [doctor], totalElements: 1, totalPages: 1 });
      if (url.startsWith('/api/doctors/9/slots')) return json([slot]);
      if (url.startsWith('/api/appointments')) { urls.push(url); return json(emptyPage); }
      return json(emptyPage);
    }));

    renderDayAgenda(`/day-agenda?doctorId=9&branchId=1&date=${today}`);
    await waitFor(() => expect(urls.length).toBeGreaterThan(0));

    const params = new URLSearchParams(urls[0].split('?')[1] ?? '');
    expect(params.get('doctorId')).toBe('9');
    expect(params.get('from')).toBe(startOfIstDayUtc(today));

    expect(await screen.findByText('09:30 – 10:00')).toBeTruthy();
    const book = screen.getByRole('link', { name: 'Book' });
    expect(book.getAttribute('href')).toContain(`/appointments/new?doctorId=9&branchId=1&date=${today}`);
  });

  it('shows a clear notice when a day has more than 100 appointments', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/doctors/search')) return json({ ...emptyPage, content: [doctor], totalElements: 1, totalPages: 1 });
      if (url.startsWith('/api/doctors/9/slots')) return json([]);
      if (url.startsWith('/api/appointments')) return json({ ...emptyPage, content: [], totalElements: 150, totalPages: 2 });
      return json(emptyPage);
    }));

    renderDayAgenda(`/day-agenda?doctorId=9&date=${today}`);
    expect(await screen.findByText(/first 100 of 150/i)).toBeTruthy();
  });
});
