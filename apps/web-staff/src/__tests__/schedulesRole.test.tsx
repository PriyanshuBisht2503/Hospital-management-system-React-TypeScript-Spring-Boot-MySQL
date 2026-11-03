// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
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

const authValue = (user: CurrentUser | null) => ({
  user,
  isAuthenticated: !!user,
  isLoading: false,
  authError: null,
  login: async () => {},
  logout: async () => {},
  clearError: () => {},
});

const doctor = (doctorId: number | null): CurrentUser => ({ id: 2, email: 'd@x', phone: null, role: 'DOCTOR', fullName: 'Dr. Test', enabled: true, doctorId });
const admin: CurrentUser = { id: 1, email: 'a@x', phone: null, role: 'ADMIN', fullName: 'Admin', enabled: true, doctorId: null };
const receptionist: CurrentUser = { id: 3, email: 'r@x', phone: null, role: 'RECEPTIONIST', fullName: 'Rec', enabled: true, doctorId: null };

const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { 'Content-Type': 'application/json' } });

const template = { id: 1, doctorId: 42, branchId: 1, branchName: 'Main', dayOfWeek: 'MONDAY', startTime: '09:00:00', endTime: '12:00:00', slotMinutes: 30, breakStart: null, breakEnd: null, validFrom: '2026-01-01', validTo: null, isActive: true };
const exception = { id: 1, doctorId: 42, date: '2999-01-01', startTime: null, endTime: null, type: 'LEAVE', reason: 'Annual leave' };

function stubScheduleFetch() {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (url.startsWith('/api/admin/doctors/42/schedules')) return json([template]);
    if (url.startsWith('/api/admin/doctors/42/exceptions')) return json([exception]);
    if (url.startsWith('/api/doctors/42')) return json({ id: 42, fullName: 'Dr. X', branches: [{ id: 1, name: 'Main', code: 'M', city: 'Dubai', isActive: true }], specialties: [], isActive: true, consultationFee: 100 });
    return new Response('Not Found', { status: 404 });
  }));
}

function renderPage(user: CurrentUser, entry: string) {
  mockUseAuth.mockReturnValue(authValue(user));
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

describe('schedules: role behaviour', () => {
  it('a DOCTOR with null doctorId sees the no-profile page', () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('Not Found', { status: 404 })));
    renderPage(doctor(null), '/schedules/me');
    expect(screen.getByRole('heading', { name: /No doctor profile is linked/i })).toBeTruthy();
  });

  it('a DOCTOR cannot open another doctor\'s page and fetches nothing', () => {
    const fetchMock = vi.fn(async () => new Response('Not Found', { status: 404 }));
    vi.stubGlobal('fetch', fetchMock);
    renderPage(doctor(42), '/schedules/99');
    expect(screen.getByRole('heading', { name: /Schedule not found/i })).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('a RECEPTIONIST sees no edit controls', async () => {
    stubScheduleFetch();
    renderPage(receptionist, '/schedules/42');
    await screen.findByRole('heading', { name: 'Weekly Templates' });

    expect(screen.queryByRole('button', { name: /Add Template/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /Add Exception/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /Edit template/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /Delete template/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /Delete exception/i })).toBeNull();
  });

  it('an ADMIN sees the edit controls', async () => {
    stubScheduleFetch();
    renderPage(admin, '/schedules/42');
    await screen.findByRole('heading', { name: 'Weekly Templates' });

    expect(screen.getByRole('button', { name: /Add Template/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Add Exception/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Edit template/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Delete template/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Delete exception/i })).toBeTruthy();
  });

  it('a DOCTOR on their own page sees read-only templates but can add exceptions', async () => {
    stubScheduleFetch();
    renderPage(doctor(42), '/schedules/42');
    await screen.findByRole('heading', { name: 'Weekly Templates' });

    expect(screen.queryByRole('button', { name: /Add Template/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /Edit template/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /Delete template/i })).toBeNull();
    expect(screen.getByRole('button', { name: /Add Exception/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Delete exception/i })).toBeTruthy();
  });
});
