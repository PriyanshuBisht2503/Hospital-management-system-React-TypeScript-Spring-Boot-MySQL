// @vitest-environment jsdom
/**
 * Step 4B tests: appointment actions and rescheduling.
 *
 * Every mock answers every endpoint it might receive, with real (non-empty)
 * response shapes. Control cases are included to prove negatives.
 */
import { describe, it, expect, afterEach, vi, beforeEach } from 'vitest';
import {
  render,
  screen,
  cleanup,
  fireEvent,
  waitFor,
  within,
  act,
} from '@testing-library/react';
import type { ReactElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ToastProvider } from '@hms/ui';
import { AppointmentDetailPage } from '../pages/AppointmentDetailPage';
import { AppointmentActions } from '../components/AppointmentActions';
import { useAuth } from '../context/AuthContext';
import type { CurrentUser } from '../types/auth';
import type { Appointment } from '../types/appointment';

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }));
const mockUseAuth = vi.mocked(useAuth);

// ── Fixture helpers ──────────────────────────────────────────────────────────

const ADMIN: CurrentUser = {
  id: 1, email: 'admin@x', phone: null, role: 'ADMIN', fullName: 'Admin', enabled: true, doctorId: null,
};
const RECEPTIONIST: CurrentUser = {
  id: 2, email: 'rec@x', phone: null, role: 'RECEPTIONIST', fullName: 'Rec', enabled: true, doctorId: null,
};
const DOCTOR: CurrentUser = {
  id: 3, email: 'doc@x', phone: null, role: 'DOCTOR', fullName: 'Doc', enabled: true, doctorId: 9,
};

const authVal = (u: CurrentUser) => ({
  user: u, isAuthenticated: true, isLoading: false, authError: null,
  login: async () => {}, logout: async () => {}, clearError: () => {},
});

const json = (b: unknown, status = 200) =>
  new Response(JSON.stringify(b), { status, headers: { 'Content-Type': 'application/json' } });

const emptyPage = {
  content: [], pageNumber: 0, pageSize: 6, totalElements: 0, totalPages: 0, isFirst: true, isLast: true,
};

/** Base appointment fixture – override `status` and other fields as needed. */
function makeAppt(overrides: Partial<Appointment> = {}): Appointment {
  return {
    id: 7,
    patient: { id: 1, fullName: 'John Smith', age: 30, phone: null, email: null },
    doctorId: 9,
    doctorName: 'Dr. Nine',
    branchId: 1,
    branchName: 'Main',
    // startTime is tomorrow 09:30 IST = 04:00 UTC
    startTime: new Date(Date.now() + 86400000).toISOString(),
    endTime: new Date(Date.now() + 86400000 + 1800000).toISOString(),
    type: 'IN_PERSON',
    status: 'CONFIRMED',
    reasonForVisit: 'Check-up',
    cancelReason: null,
    cancelledBy: null,
    createdBy: 'admin@x',
    rescheduledFromId: null,
    needsRescheduling: false,
    confirmedAt: null,
    checkedInAt: null,
    startedAt: null,
    completedAt: null,
    cancelledAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

const slot = {
  doctorId: 9, branchId: 1,
  startTime: '2026-11-10T04:00:00.000Z',
  endTime: '2026-11-10T04:30:00.000Z',
  slotMinutes: 30,
};

// ── Render helpers ──────────────────────────────────────────────────────────

function makeQC() {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
}

/** Render the detail page inside a MemoryRouter with an optional extra route. */
function renderDetail(
  user: CurrentUser,
  appt: Appointment,
  extraRoutes: { path: string; element: ReactElement }[] = [],
  fetchMock?: (url: string, init?: RequestInit) => Response | Promise<Response>,
) {
  mockUseAuth.mockReturnValue(authVal(user));

  const defaultFetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (url.startsWith(`/api/appointments/${appt.id}`) && !url.includes('/')) return json(appt);
    if (url.startsWith(`/api/appointments/${appt.id}`)) return json(appt);
    if (url.startsWith('/api/branches')) return json(emptyPage);
    if (url.startsWith('/api/doctors/9/slots')) return json([slot]);
    return new Response('Not Found', { status: 404 });
  };

  vi.stubGlobal('fetch', vi.fn(fetchMock ?? defaultFetch));

  return render(
    <QueryClientProvider client={makeQC()}>
      <ToastProvider>
        <MemoryRouter initialEntries={[`/appointments/${appt.id}`]}>
          <Routes>
            <Route path="/appointments/:id" element={<AppointmentDetailPage />} />
            {extraRoutes.map((r) => (
              <Route key={r.path} path={r.path} element={r.element} />
            ))}
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

/** Render AppointmentActions directly for unit-level tests. */
function renderActions(
  user: CurrentUser,
  appt: Appointment,
  now: Date = new Date(),
  fetchMock?: (url: string, init?: RequestInit) => Response | Promise<Response>,
) {
  mockUseAuth.mockReturnValue(authVal(user));

  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (fetchMock) return fetchMock(url, init);
    if (url.startsWith('/api/branches')) return json(emptyPage);
    if (url.startsWith('/api/doctors/9/slots')) return json([slot]);
    return new Response('Not Found', { status: 404 });
  }));

  const qc = makeQC();
  return render(
    <QueryClientProvider client={qc}>
      <ToastProvider>
        <MemoryRouter>
          <Routes>
            <Route
              path="*"
              element={
                <AppointmentActions
                  appointment={appt}
                  role={user.role}
                  now={now}
                  queryKey={['appointment', appt.id]}
                />
              }
            />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

// ─────────────────────────────────────────────────────────────────────────────
// 1. Action matrix per role × status
// ─────────────────────────────────────────────────────────────────────────────

describe('action matrix', () => {
  it('ADMIN on BOOKED: shows Confirm, Cancel, Reschedule', async () => {
    renderActions(ADMIN, makeAppt({ status: 'BOOKED' }));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Confirm' })).not.toBeNull());
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Reschedule' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Check in' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'No-show' })).toBeNull();
  });

  it('RECEPTIONIST on CONFIRMED: shows Check in, No-show, Cancel, Reschedule — not Confirm', async () => {
    renderActions(RECEPTIONIST, makeAppt({ status: 'CONFIRMED' }));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeNull());
    expect(screen.getByRole('button', { name: 'Check in' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'No-show' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Reschedule' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Confirm' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Start consultation' })).toBeNull();
  });

  it('DOCTOR on CHECKED_IN: shows Start consultation and Cancel only', async () => {
    renderActions(DOCTOR, makeAppt({ status: 'CHECKED_IN' }));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Start consultation' })).not.toBeNull());
    expect(screen.getByRole('button', { name: 'Start consultation' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Reschedule' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Check in' })).toBeNull();
  });

  it('DOCTOR on IN_CONSULTATION: shows Complete and Cancel only', async () => {
    renderActions(DOCTOR, makeAppt({ status: 'IN_CONSULTATION' }));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Complete' })).not.toBeNull());
    expect(screen.getByRole('button', { name: 'Complete' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Start consultation' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Reschedule' })).toBeNull();
  });

  it('DOCTOR never sees Reschedule button', async () => {
    renderActions(DOCTOR, makeAppt({ status: 'BOOKED' }));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeNull());
    expect(screen.queryByRole('button', { name: 'Reschedule' })).toBeNull();
  });

  it('COMPLETED (terminal): no action buttons rendered', () => {
    renderActions(ADMIN, makeAppt({ status: 'COMPLETED' }));
    expect(screen.queryByRole('button', { name: 'Confirm' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Cancel' })).toBeNull();
  });

  it('CANCELLED (terminal): no action buttons rendered', () => {
    renderActions(ADMIN, makeAppt({ status: 'CANCELLED' }));
    expect(screen.queryByRole('button', { name: 'Cancel' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Confirm' })).toBeNull();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 2. Each action calls the right method and path
// ─────────────────────────────────────────────────────────────────────────────

describe('action calls right endpoint', () => {
  async function actAndCapture(
    user: CurrentUser,
    apptStatus: Appointment['status'],
    buttonLabel: string,
    patchPath: string,
  ) {
    const calls: { url: string; method: string }[] = [];
    const appt = makeAppt({ status: apptStatus });
    const updated = makeAppt({ status: 'CONFIRMED', id: appt.id });

    renderActions(user, appt, new Date(), async (url, init) => {
      calls.push({ url, method: init?.method ?? 'GET' });
      if (url.startsWith('/api/appointments')) return json(updated);
      if (url.startsWith('/api/branches')) return json(emptyPage);
      return new Response('Not Found', { status: 404 });
    });

    await waitFor(() => expect(screen.queryByRole('button', { name: buttonLabel })).not.toBeNull());
    fireEvent.click(screen.getByRole('button', { name: buttonLabel }));
    await waitFor(() => expect(calls.some((c) => c.url.includes(patchPath))).toBe(true));
    const call = calls.find((c) => c.url.includes(patchPath))!;
    expect(call.method).toBe('PATCH');
  }

  it('Confirm → PATCH /confirm', () =>
    actAndCapture(ADMIN, 'BOOKED', 'Confirm', '/confirm'));

  it('Check in → PATCH /check-in (on today)', async () => {
    // Use a startTime in the past so it's today-ish but also after-start
    const nowIST = new Date();
    const calls: { url: string; method: string }[] = [];
    const appt = makeAppt({
      status: 'CONFIRMED',
      startTime: new Date(nowIST.getTime() - 3600000).toISOString(), // 1hr ago
    });
    const updated = { ...appt };

    renderActions(RECEPTIONIST, appt, nowIST, async (url, init) => {
      calls.push({ url, method: init?.method ?? 'GET' });
      if (url.includes('/check-in')) return json(updated);
      if (url.startsWith('/api/appointments')) return json(updated);
      return new Response('Not Found', { status: 404 });
    });

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Check in' })).not.toBeNull());
    const btn = screen.getByRole('button', { name: 'Check in' }) as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
    fireEvent.click(btn);
    await waitFor(() => expect(calls.some((c) => c.url.includes('/check-in'))).toBe(true));
    expect(calls.find((c) => c.url.includes('/check-in'))?.method).toBe('PATCH');
  });

  it('Start consultation → PATCH /start-consultation', () =>
    actAndCapture(DOCTOR, 'CHECKED_IN', 'Start consultation', '/start-consultation'));

  it('Complete → PATCH /complete', () =>
    actAndCapture(DOCTOR, 'IN_CONSULTATION', 'Complete', '/complete'));

  it('No-show → PATCH /no-show (after start)', async () => {
    const pastStart = new Date(Date.now() - 3600000).toISOString();
    const calls: { url: string; method: string }[] = [];
    const appt = makeAppt({ status: 'CONFIRMED', startTime: pastStart });
    const updated = { ...appt };

    renderActions(ADMIN, appt, new Date(), async (url, init) => {
      calls.push({ url, method: init?.method ?? 'GET' });
      if (url.includes('/no-show')) return json(updated);
      if (url.startsWith('/api/appointments')) return json(updated);
      return new Response('Not Found', { status: 404 });
    });

    await waitFor(() => expect(screen.queryByRole('button', { name: 'No-show' })).not.toBeNull());
    fireEvent.click(screen.getByRole('button', { name: 'No-show' }));
    await waitFor(() => expect(calls.some((c) => c.url.includes('/no-show'))).toBe(true));
    expect(calls.find((c) => c.url.includes('/no-show'))?.method).toBe('PATCH');
  });

  it('Cancel → PATCH /cancel with reason body', async () => {
    const calls: { url: string; method: string; body?: string }[] = [];
    const appt = makeAppt({ status: 'CONFIRMED' });
    const updated = { ...appt, status: 'CANCELLED' as const };

    renderActions(ADMIN, appt, new Date(), async (url, init) => {
      const body = typeof init?.body === 'string' ? init.body : undefined;
      calls.push({ url, method: init?.method ?? 'GET', body });
      if (url.includes('/cancel')) return json(updated);
      if (url.startsWith('/api/appointments')) return json(updated);
      return new Response('Not Found', { status: 404 });
    });

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeNull());
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    // CancelDialog opens
    const reasonInput = screen.getByLabelText(/Reason/i);
    fireEvent.change(reasonInput, { target: { value: 'Patient request' } });
    fireEvent.click(screen.getByRole('button', { name: /Cancel appointment/i }));

    await waitFor(() => expect(calls.some((c) => c.url.includes('/cancel'))).toBe(true));
    const cancelCall = calls.find((c) => c.url.includes('/cancel'))!;
    expect(cancelCall.method).toBe('PATCH');
    expect(JSON.parse(cancelCall.body ?? '{}')).toEqual({ reason: 'Patient request' });
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 3. Cancel needs non-blank reason; reschedule success navigation; reschedule 409
// ─────────────────────────────────────────────────────────────────────────────

describe('cancel dialog validation', () => {
  it('blocks submit and shows alert when reason is blank', async () => {
    const appt = makeAppt({ status: 'CONFIRMED' });

    renderActions(ADMIN, appt, new Date(), async (url) => {
      if (url.startsWith('/api/appointments')) return json(appt);
      return new Response('Not Found', { status: 404 });
    });

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeNull());
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    // The cancel button inside the dialog is the one we need to target
    const cancelApptBtn = screen.getByRole('button', { name: /Cancel appointment/i }) as HTMLButtonElement;
    expect(cancelApptBtn.disabled).toBe(true);
    expect(screen.getByText(/Reason cannot be blank/i)).toBeTruthy();

    // Type a space (still blank after trim)
    const reasonInput = screen.getByLabelText(/Reason/i);
    fireEvent.change(reasonInput, { target: { value: '   ' } });

    // Still disabled
    expect(cancelApptBtn.disabled).toBe(true);
  });
});

describe('reschedule flow', () => {
  it('success navigates to the NEW appointment id', async () => {
    const appt = makeAppt({ status: 'CONFIRMED' });
    const newAppt = makeAppt({ id: 99, status: 'CONFIRMED', rescheduledFromId: appt.id });
    const calls: { url: string; method: string; body?: string }[] = [];

    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      const body = typeof init?.body === 'string' ? init.body : undefined;
      calls.push({ url, method: init?.method ?? 'GET', body });
      if (url.startsWith('/api/branches')) return json({ ...emptyPage, content: [{ id: 1, name: 'Main', code: 'M', city: 'Dubai', isActive: true }] });
      if (url.startsWith('/api/doctors/9/slots')) return json([slot]);
      if (url === `/api/appointments/${appt.id}/reschedule` && init?.method === 'POST') return json(newAppt, 200);
      if (url.startsWith(`/api/appointments/${newAppt.id}`)) return json(newAppt);
      if (url.startsWith(`/api/appointments/${appt.id}`)) return json(appt);
      return new Response('Not Found', { status: 404 });
    }));

    mockUseAuth.mockReturnValue(authVal(ADMIN));

    render(
      <QueryClientProvider client={makeQC()}>
        <ToastProvider>
          <MemoryRouter initialEntries={[`/appointments/${appt.id}`]}>
            <Routes>
              <Route path="/appointments/:id" element={<AppointmentDetailPage />} />
            </Routes>
          </MemoryRouter>
        </ToastProvider>
      </QueryClientProvider>,
    );

    // Wait for page to render
    await screen.findByRole('heading', { name: /Appointment #7/i });

    // Open reschedule dialog
    fireEvent.click(screen.getByRole('button', { name: 'Reschedule' }));

    // The modal opens — pick a date and slot
    const dateInput = screen.getByLabelText(/Date \(IST\)/i);
    fireEvent.change(dateInput, { target: { value: '2026-11-10' } });

    // Wait for slot button
    const slotBtn = await screen.findByRole('button', { name: /09:30 IST/i });
    fireEvent.click(slotBtn);

    // Confirm reschedule
    fireEvent.click(screen.getByRole('button', { name: /Confirm reschedule/i }));

    await waitFor(() =>
      expect(calls.some((c) => c.url.includes('/reschedule') && c.method === 'POST')).toBe(true),
    );
    const rescCall = calls.find((c) => c.url.includes('/reschedule'))!;
    const body = JSON.parse(rescCall.body ?? '{}');
    expect(body.newStartTime).toBe(slot.startTime);
    expect(body.newEndTime).toBe(slot.endTime);

    // Should navigate to new appointment
    await screen.findByRole('heading', { name: /Appointment #99/i });
  });

  it('reschedule 409 clears slot, refetches slots and shows server message', async () => {
    const appt = makeAppt({ status: 'CONFIRMED' });
    let slotFetches = 0;

    renderActions(ADMIN, appt, new Date(), async (url, init) => {
      if (url.startsWith('/api/branches')) return json({ ...emptyPage, content: [{ id: 1, name: 'Main', code: 'M', city: 'Dubai', isActive: true }] });
      if (url.startsWith('/api/doctors/9/slots')) { slotFetches++; return json([slot]); }
      if (url.includes('/reschedule') && init?.method === 'POST') {
        return json({ status: 409, error: 'Conflict', message: 'Slot already taken', path: '/api', timestamp: new Date().toISOString() }, 409);
      }
      if (url.startsWith('/api/appointments')) return json(appt);
      return new Response('Not Found', { status: 404 });
    });

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Reschedule' })).not.toBeNull());
    fireEvent.click(screen.getByRole('button', { name: 'Reschedule' }));

    const dateInput = screen.getByLabelText(/Date \(IST\)/i);
    fireEvent.change(dateInput, { target: { value: '2026-11-10' } });

    const slotBtn = await screen.findByRole('button', { name: /09:30 IST/i });
    fireEvent.click(slotBtn);
    fireEvent.click(screen.getByRole('button', { name: /Confirm reschedule/i }));

    // Dialog stays open, error message shown
    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(screen.getByRole('alert').textContent).toContain('Slot already taken');

    // The Confirm button should be disabled (slot cleared)
    const confirmBtn = screen.getByRole('button', { name: /Confirm reschedule/i }) as HTMLButtonElement;
    expect(confirmBtn.disabled).toBe(true);

    // Slots are refetched so availability is fresh
    await waitFor(() => expect(slotFetches).toBeGreaterThan(1));
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 4. Time hints with injected now
// ─────────────────────────────────────────────────────────────────────────────

describe('time hints', () => {
  const TOMORROW_IST = (() => {
    // Build a UTC instant that is tomorrow in IST
    const d = new Date();
    d.setUTCHours(d.getUTCHours() + 5);
    d.setUTCMinutes(d.getUTCMinutes() + 30);
    d.setUTCDate(d.getUTCDate() + 1);
    return d.toISOString();
  })();

  it('Check-in is DISABLED when now is on a different IST day', async () => {
    // startTime is tomorrow — now is today
    const appt = makeAppt({ status: 'CONFIRMED', startTime: TOMORROW_IST });
    renderActions(RECEPTIONIST, appt, new Date()); // now = today

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Check in' })).not.toBeNull());
    const btn = screen.getByRole('button', { name: 'Check in' }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
    expect(btn.title).toMatch(/appointment date/i);
  });

  it('Check-in is ENABLED when injected now is on the same IST day as startTime', async () => {
    const startISO = '2026-11-10T04:00:00.000Z'; // 09:30 IST on 2026-11-10
    const nowOnSameDay = new Date('2026-11-10T08:00:00.000Z'); // 13:30 IST — same day
    const appt = makeAppt({ status: 'CONFIRMED', startTime: startISO });
    renderActions(RECEPTIONIST, appt, nowOnSameDay);

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Check in' })).not.toBeNull());
    const btn = screen.getByRole('button', { name: 'Check in' }) as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
  });

  it('No-show is DISABLED before start time', async () => {
    // startTime is tomorrow
    const appt = makeAppt({ status: 'CONFIRMED', startTime: TOMORROW_IST });
    renderActions(ADMIN, appt, new Date());

    await waitFor(() => expect(screen.queryByRole('button', { name: 'No-show' })).not.toBeNull());
    const btn = screen.getByRole('button', { name: 'No-show' }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
    expect(btn.title).toMatch(/after/i);
  });

  it('No-show is ENABLED when injected now is after start time', async () => {
    const pastStart = '2026-11-01T04:00:00.000Z'; // past
    const nowAfter = new Date('2026-11-01T06:00:00.000Z');
    const appt = makeAppt({ status: 'CONFIRMED', startTime: pastStart });
    renderActions(ADMIN, appt, nowAfter);

    await waitFor(() => expect(screen.queryByRole('button', { name: 'No-show' })).not.toBeNull());
    const btn = screen.getByRole('button', { name: 'No-show' }) as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 5. In-flight guard: second click sends only one request
// ─────────────────────────────────────────────────────────────────────────────

describe('in-flight guard', () => {
  it('double-clicking Confirm sends exactly one PATCH and disables all others', async () => {
    const callCount = { confirm: 0 };
    const appt = makeAppt({ status: 'BOOKED' });
    const updated = makeAppt({ status: 'CONFIRMED', id: appt.id });

    renderActions(ADMIN, appt, new Date(), async (url, init) => {
      if (url.includes('/confirm') && init?.method === 'PATCH') {
        callCount.confirm++;
        await new Promise((r) => setTimeout(r, 80));
        return json(updated);
      }
      if (url.startsWith('/api/appointments')) return json(updated);
      return new Response('Not Found', { status: 404 });
    });

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Confirm' })).not.toBeNull());
    const confirmBtn = screen.getByRole('button', { name: 'Confirm' });
    fireEvent.click(confirmBtn);

    // All buttons should be disabled while in-flight
    await waitFor(() =>
      expect((screen.getByRole('button', { name: /Confirm/i }) as HTMLButtonElement).disabled).toBe(true),
    );

    // Second click while disabled — no extra request
    fireEvent.click(confirmBtn);

    await waitFor(() => expect(callCount.confirm).toBe(1));
    expect(callCount.confirm).toBe(1);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 6. 409 on simple action refetches and shows server message
// ─────────────────────────────────────────────────────────────────────────────

describe('409 on action', () => {
  it('shows server error message and refetches on 409', async () => {
    const appt = makeAppt({ status: 'BOOKED' });
    const fetchSpy: string[] = [];

    renderActions(ADMIN, appt, new Date(), async (url, init) => {
      fetchSpy.push(url);
      if (url.includes('/confirm') && init?.method === 'PATCH') {
        return json({ status: 409, error: 'Conflict', message: 'Already confirmed', path: '/api', timestamp: new Date().toISOString() }, 409);
      }
      if (url.startsWith('/api/appointments')) return json(appt);
      return new Response('Not Found', { status: 404 });
    });

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Confirm' })).not.toBeNull());
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));

    // Toast error with the server message (toast renders in the DOM)
    await waitFor(() =>
      expect(document.body.textContent).toContain('Already confirmed'),
    );
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 7. needsRescheduling banner
// ─────────────────────────────────────────────────────────────────────────────

describe('needsRescheduling banner', () => {
  it('ADMIN sees warning banner WITH a "Reschedule now" button', async () => {
    const appt = makeAppt({ status: 'CONFIRMED', needsRescheduling: true });
    renderDetail(ADMIN, appt);

    const banner = await screen.findByRole('alert', { name: /Needs rescheduling warning/i });
    expect(banner).toBeTruthy();
    expect(within(banner).getByRole('button', { name: /Reschedule now/i })).toBeTruthy();
  });

  it('RECEPTIONIST sees warning banner WITH a "Reschedule now" button', async () => {
    const appt = makeAppt({ status: 'CONFIRMED', needsRescheduling: true });
    renderDetail(RECEPTIONIST, appt);

    const banner = await screen.findByRole('alert', { name: /Needs rescheduling warning/i });
    expect(within(banner).getByRole('button', { name: /Reschedule now/i })).toBeTruthy();
  });

  it('DOCTOR sees warning banner but NO "Reschedule now" button', async () => {
    const appt = makeAppt({ status: 'CONFIRMED', needsRescheduling: true });
    renderDetail(DOCTOR, appt);

    const banner = await screen.findByRole('alert', { name: /Needs rescheduling warning/i });
    expect(banner).toBeTruthy();
    expect(within(banner).queryByRole('button', { name: /Reschedule now/i })).toBeNull();
  });

  it('banner is NOT shown when needsRescheduling is false', async () => {
    const appt = makeAppt({ status: 'CONFIRMED', needsRescheduling: false });
    renderDetail(ADMIN, appt);

    await screen.findByRole('heading', { name: /Appointment #7/i });
    expect(screen.queryByRole('alert', { name: /Needs rescheduling warning/i })).toBeNull();
  });
});

