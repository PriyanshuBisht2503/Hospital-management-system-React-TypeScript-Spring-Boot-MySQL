// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '@hms/ui';
import { NewPatientPage } from '../pages/NewPatientPage';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('NewPatientPage: server fieldErrors', () => {
  it('shows server-side fieldErrors under the correct fields', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(
          JSON.stringify({
            status: 400,
            error: 'Validation Failed',
            message: 'Validation failed',
            path: '/api/patients',
            timestamp: new Date().toISOString(),
            fieldErrors: { email: 'Email already registered' },
          }),
          { status: 400, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <MemoryRouter initialEntries={['/patients/new']}>
            <NewPatientPage />
          </MemoryRouter>
        </ToastProvider>
      </QueryClientProvider>,
    );

    fireEvent.change(screen.getByLabelText(/Full Name/i), { target: { value: 'John Smith' } });
    fireEvent.change(screen.getByLabelText(/Phone Number/i), { target: { value: '+971501234567' } });
    fireEvent.change(screen.getByLabelText(/Email Address/i), { target: { value: 'taken@example.com' } });

    fireEvent.click(screen.getByRole('button', { name: /Register Patient/i }));

    expect(await screen.findByText('Email already registered')).toBeTruthy();
  });
});
