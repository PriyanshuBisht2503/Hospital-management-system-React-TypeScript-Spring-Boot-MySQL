// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ToastProvider } from '@hms/ui';
import { DoctorSchedulePage } from '../pages/DoctorSchedulePage';
import { useAuth } from '../context/AuthContext';
import type { CurrentUser } from '../types/auth';

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

const mockUseAuth = vi.mocked(useAuth);

const admin: CurrentUser = { id: 1, email: 'a@x', phone: null, role: 'ADMIN', fullName: 'Admin', enabled: true, doctorId: null };

const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { 'Content-Type': 'application/json' } });

const emptyPage = { content: [], pageNumber: 0, pageSize: 6, totalElements: 0, totalPages: 0, isFirst: true, isLast: true };

function renderPage(entry = '/schedules/42') {
  mockUseAuth.mockReturnValue({
    user: admin, isAuthenticated: true, isLoading: false, authError: null,
    login: async () => {}, logout: async () => {}, clearError: () => {},
  });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={[entry]}>
          <Routes>
            <Route path="/schedules/:doctorId" element={<DoctorSchedulePage />} />
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

describe('schedule flows', () => {
  it('force exception dialog: confirm re-sends with force=true', async () => {
    const postBodies: unknown[] = [];
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/admin/doctors/42/schedules')) return json([]);
      if (url.startsWith('/api/admin/doctors/42/exceptions')) {
        if (init?.method === 'POST') {
          const body = JSON.parse(init.body as string);
          postBodies.push(body);
          if (body.force === true) return json({ id: 9, doctorId: 42, date: body.date, type: body.type, startTime: null, endTime: null, reason: null }, 201);
          return json({ status: 409, error: 'Conflict', message: 'Doctor has 1 active appointments during this time window (IDs: [7]). Use force=true to proceed and mark them for rescheduling.', path: '/api', timestamp: new Date().toISOString() }, 409);
        }
        return json([]);
      }
      if (url.startsWith('/api/doctors/42')) return json({ id: 42, fullName: 'Dr. X', branches: [{ id: 1, name: 'Main', code: 'M', city: 'Dubai', isActive: true }], specialties: [], isActive: true, consultationFee: 100 });
      if (url.startsWith('/api/appointments')) {
        return json({ ...emptyPage, content: [{ id: 7, patient: { id: 1, fullName: 'John Smith', age: null, phone: null, email: null }, doctorId: 42, doctorName: 'Dr. X', branchId: 1, branchName: 'Main', startTime: '2026-11-03T04:00:00.000Z', endTime: '2026-11-03T04:30:00.000Z', type: 'IN_PERSON', status: 'CONFIRMED', reasonForVisit: null, cancelReason: null, cancelledBy: null, createdBy: null, rescheduledFromId: null, needsRescheduling: false, confirmedAt: null, checkedInAt: null, startedAt: null, completedAt: null, cancelledAt: null, createdAt: '2026-11-01T00:00:00Z' }] });
      }
      return new Response('Not Found', { status: 404 });
    }));

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /Add Exception/i }));
    fireEvent.change(screen.getByLabelText(/Date/i), { target: { value: '2026-11-03' } });
    fireEvent.click(screen.getByRole('button', { name: /Create Exception/i }));

    expect(await screen.findByText('John Smith')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: /Confirm & Mark for Rescheduling/i }));

    await waitFor(() => {
      expect(postBodies).toContainEqual(expect.objectContaining({ force: true, date: '2026-11-03', type: 'LEAVE' }));
    });
  });

  it('force exception dialog: cancel does not re-send', async () => {
    const postBodies: unknown[] = [];
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/admin/doctors/42/schedules')) return json([]);
      if (url.startsWith('/api/admin/doctors/42/exceptions')) {
        if (init?.method === 'POST') {
          postBodies.push(JSON.parse(init.body as string));
          return json({ status: 409, error: 'Conflict', message: 'Doctor has 1 active appointments during this time window (IDs: [7]). Use force=true to proceed.', path: '/api', timestamp: new Date().toISOString() }, 409);
        }
        return json([]);
      }
      if (url.startsWith('/api/doctors/42')) return json({ id: 42, fullName: 'Dr. X', branches: [{ id: 1, name: 'Main', code: 'M', city: 'Dubai', isActive: true }], specialties: [], isActive: true, consultationFee: 100 });
      if (url.startsWith('/api/appointments')) return json({ ...emptyPage, content: [] });
      return new Response('Not Found', { status: 404 });
    }));

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /Add Exception/i }));
    fireEvent.change(screen.getByLabelText(/Date/i), { target: { value: '2026-11-03' } });
    fireEvent.click(screen.getByRole('button', { name: /Create Exception/i }));

    fireEvent.click(await screen.findByRole('button', { name: /Cancel/i }));

    expect(postBodies).toHaveLength(1);
    expect(postBodies[0]).toEqual(expect.objectContaining({ force: false }));
  });

  it('delete template 409 offers "Deactivate Instead"', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/admin/doctors/42/schedules')) {
        if (init?.method === 'DELETE') {
          return json({ status: 409, error: 'Conflict', message: 'Cannot deactivate or delete schedule: doctor has future active appointments scheduled on MONDAY', path: '/api', timestamp: new Date().toISOString() }, 409);
        }
        return json([{ id: 1, doctorId: 42, branchId: 1, branchName: 'Main', dayOfWeek: 'MONDAY', startTime: '09:00:00', endTime: '12:00:00', slotMinutes: 30, breakStart: null, breakEnd: null, validFrom: '2026-01-01', validTo: null, isActive: true }]);
      }
      if (url.startsWith('/api/admin/doctors/42/exceptions')) return json([]);
      if (url.startsWith('/api/doctors/42')) return json({ id: 42, fullName: 'Dr. X', branches: [{ id: 1, name: 'Main', code: 'M', city: 'Dubai', isActive: true }], specialties: [], isActive: true, consultationFee: 100 });
      return new Response('Not Found', { status: 404 });
    }));

    renderPage();

    await screen.findByText(/MONDAY/i);
    fireEvent.click(screen.getByRole('button', { name: /Delete template/i }));

    expect(await screen.findByText(/Cannot Delete Schedule/i)).toBeTruthy();
    expect(screen.getByRole('button', { name: /Deactivate Instead/i })).toBeTruthy();
  });

  it('template create server 409 shows the server message', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/admin/doctors/42/schedules')) {
        if (init?.method === 'POST') {
          return json({ status: 409, error: 'Conflict', message: 'Doctor already has an active schedule on MONDAY overlapping this time window', path: '/api', timestamp: new Date().toISOString() }, 409);
        }
        return json([]);
      }
      if (url.startsWith('/api/admin/doctors/42/exceptions')) return json([]);
      if (url.startsWith('/api/doctors/42')) return json({ id: 42, fullName: 'Dr. X', branches: [{ id: 1, name: 'Main', code: 'M', city: 'Dubai', isActive: true }], specialties: [], isActive: true, consultationFee: 100 });
      return new Response('Not Found', { status: 404 });
    }));

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: /Add Template/i }));
    fireEvent.change(screen.getByLabelText(/Branch/i), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText(/Start Time/i), { target: { value: '09:00' } });
    fireEvent.change(screen.getByLabelText(/End Time/i), { target: { value: '17:00' } });
    fireEvent.change(screen.getByLabelText(/Valid From/i), { target: { value: '2026-01-01' } });
    fireEvent.click(screen.getByRole('button', { name: /Create Template/i }));

    expect(await screen.findByText(/overlapping this time window/i)).toBeTruthy();
  });
});
