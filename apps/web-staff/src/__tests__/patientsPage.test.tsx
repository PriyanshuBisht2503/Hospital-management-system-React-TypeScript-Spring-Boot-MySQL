// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { PatientsPage } from '../pages/PatientsPage';

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: {
      id: 2,
      email: 'doctor@hms.local',
      phone: null,
      role: 'DOCTOR',
      fullName: 'Dr. Test',
      enabled: true,
      doctorId: 42,
    },
    isAuthenticated: true,
    isLoading: false,
    authError: null,
    login: async () => {},
    logout: async () => {},
    clearError: () => {},
  }),
}));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('PatientsPage: DOCTOR masking + query params', () => {
  it('shows "Hidden" for a DOCTOR with null phone/email and sends the right query', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/patients')) {
        return new Response(
          JSON.stringify({
            content: [
              {
                id: 1,
                userId: null,
                fullName: 'John Smith',
                phone: null,
                email: null,
                dateOfBirth: null,
                age: null,
                gender: null,
                createdAt: '2026-01-01T00:00:00Z',
              },
            ],
            pageNumber: 0,
            pageSize: 6,
            totalElements: 1,
            totalPages: 1,
            isFirst: true,
            isLast: true,
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }
      return new Response('Not Found', { status: 404 });
    });
    vi.stubGlobal('fetch', fetchMock);

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/patients']}>
          <PatientsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(await screen.findByText('John Smith')).toBeTruthy();
    expect(screen.getAllByText('Hidden')).toHaveLength(2);

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith('/api/patients?page=0&size=6', expect.anything());
    });

    fireEvent.change(screen.getByPlaceholderText(/Search by name, phone, or email/i), {
      target: { value: 'Ali' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Search/i }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith('/api/patients?search=Ali&page=0&size=6', expect.anything());
    });
  });
});
