// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor, within } from '@testing-library/react';
import type { ReactElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ToastProvider } from '@hms/ui';
import { AppointmentsPage } from '../pages/AppointmentsPage';
import { AppointmentDetailPage } from '../pages/AppointmentDetailPage';
import { PatientDetailPage } from '../pages/PatientDetailPage';
import { BookingWizardPage } from '../pages/BookingWizardPage';
import { SlotPicker } from '../components/SlotPicker';
import { startOfIstDayUtc, toIstDateString } from '../lib/time';
import { useAuth } from '../context/AuthContext';
import type { CurrentUser } from '../types/auth';

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }));
const mockUseAuth = vi.mocked(useAuth);

const admin: CurrentUser = { id: 1, email: 'a@x', phone: null, role: 'ADMIN', fullName: 'Admin', enabled: true, doctorId: null };
const doctorUser: CurrentUser = { id: 3, email: 'd@x', phone: null, role: 'DOCTOR', fullName: 'Doc', enabled: true, doctorId: 42 };

const authValue = (user: CurrentUser) => ({ user, isAuthenticated: true, isLoading: false, authError: null, login: async () => {}, logout: async () => {}, clearError: () => {} });

const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { 'Content-Type': 'application/json' } });
const emptyPage = { content: [], pageNumber: 0, pageSize: 6, totalElements: 0, totalPages: 0, isFirst: true, isLast: true };

function renderRoutes(user: CurrentUser, entry: string, routes: { path: string; element: ReactElement }[]) {
  mockUseAuth.mockReturnValue(authValue(user));
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <ToastProvider>
        <MemoryRouter initialEntries={[entry]}>
          <Routes>
            {routes.map((r) => <Route key={r.path} path={r.path} element={r.element} />)}
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

const renderRoute = (user: CurrentUser, path: string, entry: string, element: ReactElement) =>
  renderRoutes(user, entry, [{ path, element }]);

function searchParamsOf(url: string): URLSearchParams {
  return new URLSearchParams(url.split('?')[1] ?? '');
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const appointment = {
  id: 7, patient: { id: 1, fullName: 'John Smith', age: null, phone: null, email: null }, doctorId: 9, doctorName: 'Dr. Nine', branchId: 1, branchName: 'Main',
  startTime: '2026-11-03T04:00:00.000Z', endTime: '2026-11-03T04:30:00.000Z', type: 'IN_PERSON', status: 'CONFIRMED', reasonForVisit: null, cancelReason: null, cancelledBy: null,
  createdBy: 'admin', rescheduledFromId: null, needsRescheduling: true, confirmedAt: null, checkedInAt: null, startedAt: null, completedAt: null, cancelledAt: null, createdAt: '2026-11-01T00:00:00Z',
};

const patient = { id: 1, userId: null, fullName: 'John Smith', phone: '+971501234567', email: 'j@x.com', dateOfBirth: null, age: null, gender: null, createdAt: '2026-01-01T00:00:00Z' };

const doctor = { id: 9, fullName: 'Dr. Nine', branches: [{ id: 1, name: 'Main', code: 'M', city: 'Dubai', isActive: true }], specialties: [], isActive: true, consultationFee: 100, nextAvailableSlot: null };

const slot = { doctorId: 9, branchId: 1, startTime: '2026-11-05T04:00:00.000Z', endTime: '2026-11-05T04:30:00.000Z', slotMinutes: 30 };

describe('appointments: list filters and badge', () => {
  it('converts IST dates to UTC instants, sends needsRescheduling=true, and shows the badge', async () => {
    const urls: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/appointments')) { urls.push(url); return json({ ...emptyPage, content: [appointment], totalElements: 1, totalPages: 1 }); }
      if (url.startsWith('/api/doctors/search')) return json(emptyPage);
      if (url.startsWith('/api/branches')) return json(emptyPage);
      return new Response('Not Found', { status: 404 });
    }));

    renderRoute(admin, '/appointments', '/appointments?from=2026-11-03&to=2026-11-03&needsRescheduling=true', <AppointmentsPage />);

    expect(await screen.findByText('John Smith')).toBeTruthy();
    const row = screen.getByText('John Smith').closest('tr') as HTMLElement;
    expect(within(row).getByText(/Needs rescheduling/i)).toBeTruthy();

    const listUrl = urls.find((u) => u.startsWith('/api/appointments'))!;
    const params = searchParamsOf(listUrl);
    expect(params.get('from')).toBe('2026-11-02T18:30:00.000Z');
    expect(params.get('to')).toBe('2026-11-03T18:30:00.000Z');
    expect(params.get('needsRescheduling')).toBe('true');
  });

  it('defaults the request to From = today (IST) with no end date', async () => {
    const urls: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/appointments')) { urls.push(url); return json(emptyPage); }
      if (url.startsWith('/api/doctors/search')) return json(emptyPage);
      if (url.startsWith('/api/branches')) return json(emptyPage);
      return new Response('Not Found', { status: 404 });
    }));

    renderRoute(admin, '/appointments', '/appointments', <AppointmentsPage />);

    await waitFor(() => expect(urls.length).toBeGreaterThan(0));
    const listUrl = urls.find((u) => u.startsWith('/api/appointments'))!;
    const params = searchParamsOf(listUrl);
    const today = toIstDateString(new Date());
    expect(params.get('from')).toBe(startOfIstDayUtc(today));
    expect(params.get('to')).toBeNull();
  });
});

describe('patient detail: book appointment button', () => {
  it('is shown for ADMIN', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/patients/1')) return json(patient);
      if (url.startsWith('/api/appointments')) return json(emptyPage);
      return new Response('Not Found', { status: 404 });
    }));
    renderRoute(admin, '/patients/:id', '/patients/1', <PatientDetailPage />);
    expect(await screen.findByRole('button', { name: /Book Appointment/i })).toBeTruthy();
  });

  it('is hidden for DOCTOR', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/patients/1')) return json(patient);
      if (url.startsWith('/api/appointments')) return json(emptyPage);
      return new Response('Not Found', { status: 404 });
    }));
    renderRoute(doctorUser, '/patients/:id', '/patients/1', <PatientDetailPage />);
    await screen.findByText('John Smith');
    expect(screen.queryByRole('button', { name: /Book Appointment/i })).toBeNull();
  });
});

describe('appointment detail', () => {
  it('shows the same Appointment not found page for a 404', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ status: 404, error: 'Not Found', message: 'Appointment not found: 999', path: '/api/appointments/999', timestamp: '2026-01-01T00:00:00Z' }, 404)));
    renderRoute(admin, '/appointments/:id', '/appointments/999', <AppointmentDetailPage />);
    expect(await screen.findByRole('heading', { name: /Appointment not found/i })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: /Appointment #999/i })).toBeNull();
  });
});

describe('SlotPicker', () => {
  it('shows the empty state when there are no slots', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json([])));
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <SlotPicker doctorId={9} branchId={1} date="2026-11-05" selected={null} onSelect={() => {}} />
      </QueryClientProvider>,
    );
    expect(await screen.findByText(/No slots this day/i)).toBeTruthy();
  });
});

describe('booking wizard', () => {
  const wizardRoutes = [
    { path: '/appointments/new', element: <BookingWizardPage /> },
    { path: '/appointments/:id', element: <AppointmentDetailPage /> },
  ];

  const walkToBook = async () => {
    await screen.findByLabelText(/Doctor/i);
    fireEvent.change(screen.getByLabelText(/Doctor/i), { target: { value: '9' } });
    fireEvent.change(screen.getByLabelText(/Branch/i), { target: { value: '1' } });
    fireEvent.click(screen.getByRole('button', { name: /Next/i }));
    fireEvent.change(screen.getByLabelText(/Date/i), { target: { value: new Date(Date.now() + 86400000 * 3).toISOString().slice(0, 10) } });
    fireEvent.click(screen.getByRole('button', { name: /Next/i }));
    fireEvent.click(await screen.findByRole('button', { name: /09:30 IST/i }));
  };

  it('preselects the patient from ?patientId', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/patients/1')) return json(patient);
      if (url.startsWith('/api/doctors/search')) return json(emptyPage);
      return new Response('Not Found', { status: 404 });
    }));
    renderRoutes(admin, '/appointments/new?patientId=1', [{ path: '/appointments/new', element: <BookingWizardPage /> }]);
    expect(await screen.findByText('John Smith')).toBeTruthy();
  });

  it('happy path posts the exact slot instants and lands on the detail page', async () => {
    const posts: Record<string, unknown>[] = [];
    const created = { ...appointment, id: 100, needsRescheduling: false, startTime: slot.startTime, endTime: slot.endTime };
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/patients/1')) return json(patient);
      if (url.startsWith('/api/doctors/search')) return json({ ...emptyPage, content: [doctor] });
      if (url.startsWith('/api/doctors/9/slots')) return json([slot]);
      if (url === '/api/appointments' && init?.method === 'POST') { posts.push(JSON.parse(init.body as string)); return json(created, 201); }
      if (url.startsWith('/api/appointments/100')) return json(created);
      return new Response('Not Found', { status: 404 });
    }));

    renderRoutes(admin, '/appointments/new?patientId=1', wizardRoutes);
    await walkToBook();
    fireEvent.click(screen.getByRole('button', { name: /Book Appointment/i }));

    await waitFor(() => expect(posts.length).toBe(1));
    expect(posts[0]).toEqual(expect.objectContaining({ patientId: 1, doctorId: 9, branchId: 1, startTime: slot.startTime, endTime: slot.endTime }));
    expect(await screen.findByRole('heading', { name: /Appointment #100/i })).toBeTruthy();
  });

  it('double-clicking Book sends only one POST (button disabled while in flight)', async () => {
    const posts: Record<string, unknown>[] = [];
    const created = { ...appointment, id: 100, needsRescheduling: false, startTime: slot.startTime, endTime: slot.endTime };
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/patients/1')) return json(patient);
      if (url.startsWith('/api/doctors/search')) return json({ ...emptyPage, content: [doctor] });
      if (url.startsWith('/api/doctors/9/slots')) return json([slot]);
      if (url === '/api/appointments' && init?.method === 'POST') {
        posts.push(JSON.parse(init.body as string));
        await new Promise((r) => setTimeout(r, 100));
        return json(created, 201);
      }
      return new Response('Not Found', { status: 404 });
    }));

    renderRoutes(admin, '/appointments/new?patientId=1', wizardRoutes);
    await walkToBook();
    const bookButton = screen.getByRole('button', { name: /Book Appointment/i }) as HTMLButtonElement;
    fireEvent.click(bookButton);
    await waitFor(() => expect(bookButton.disabled).toBe(true));
    fireEvent.click(bookButton);
    expect(posts.length).toBe(1);
  });

  it('conflict (409) clears the selection and stays on the slot step', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/patients/1')) return json(patient);
      if (url.startsWith('/api/doctors/search')) return json({ ...emptyPage, content: [doctor] });
      if (url.startsWith('/api/doctors/9/slots')) return json([slot]);
      if (url === '/api/appointments' && init?.method === 'POST') return json({ status: 409, error: 'Conflict', message: 'Doctor already has an active appointment overlapping this time window', path: '/api', timestamp: new Date().toISOString() }, 409);
      return new Response('Not Found', { status: 404 });
    }));

    renderRoutes(admin, '/appointments/new?patientId=1', wizardRoutes);
    await walkToBook();
    fireEvent.click(screen.getByRole('button', { name: /Book Appointment/i }));

    expect(await screen.findByText(/just taken/i)).toBeTruthy();
    expect(screen.getByRole('button', { name: /09:30 IST/i })).toBeTruthy();
  });
});
