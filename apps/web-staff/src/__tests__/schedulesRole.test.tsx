// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
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

const doctor = (doctorId: number | null): CurrentUser => ({
  id: 2, email: 'd@x', phone: null, role: 'DOCTOR', fullName: 'Dr. Test', enabled: true, doctorId,
});

function renderPage(user: CurrentUser, entry: string) {
  mockUseAuth.mockReturnValue(authValue(user));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={[entry]}>
          <DoctorSchedulePage />
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
    expect(screen.getByText(/No doctor profile is linked to your account/i)).toBeTruthy();
  });

  it('a DOCTOR cannot open another doctor\'s page and fetches nothing', () => {
    const fetchMock = vi.fn(async () => new Response('Not Found', { status: 404 }));
    vi.stubGlobal('fetch', fetchMock);
    renderPage(doctor(42), '/schedules/99');
    expect(screen.getByText(/Schedule not found/i)).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('a RECEPTIONIST sees no edit controls', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      const json = (b: unknown) => new Response(JSON.stringify(b), { status: 200, headers: { 'Content-Type': 'application/json' } });
      if (url.startsWith('/api/admin/doctors/42/schedules')) return json([]);
      if (url.startsWith('/api/admin/doctors/42/exceptions')) return json([]);
      if (url.startsWith('/api/doctors/42')) return json({ id: 42, fullName: 'Dr. X', branches: [], specialties: [], isActive: true, consultationFee: 100 });
      return new Response('Not Found', { status: 404 });
    }));
    renderPage({ id: 3, email: 'r@x', phone: null, role: 'RECEPTIONIST', fullName: 'Rec', enabled: true, doctorId: null }, '/schedules/42');
    await screen.findByText(/Weekly Templates/i);
    expect(screen.queryByText(/Add Template/i)).toBeNull();
    expect(screen.queryByText(/Add Exception/i)).toBeNull();
  });
});
