// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import App from '../App';

const refreshBody = (user: Record<string, unknown>) =>
  new Response(
    JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 1800, user }),
    { status: 200, headers: { 'Content-Type': 'application/json' } },
  );

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('patient routes: role-based access', () => {
  beforeEach(() => {
    window.history.pushState({}, '', '/patients/new');
  });

  it('a DOCTOR cannot open /patients/new', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url === '/api/auth/refresh') {
          return refreshBody({
            id: 2,
            email: 'doctor@hms.local',
            phone: null,
            role: 'DOCTOR',
            fullName: 'Dr. Test',
            enabled: true,
            doctorId: 42,
          });
        }
        return new Response('Not Found', { status: 404 });
      }),
    );

    render(<App />);

    expect(await screen.findByText(/Access Denied/i)).toBeTruthy();
    expect(screen.queryByText(/New Walk-in Patient/i)).toBeNull();
  });

  it('a RECEPTIONIST can open /patients/new', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url === '/api/auth/refresh') {
          return refreshBody({
            id: 3,
            email: 'receptionist@hms.local',
            phone: null,
            role: 'RECEPTIONIST',
            fullName: 'Rec Test',
            enabled: true,
            doctorId: null,
          });
        }
        return new Response('Not Found', { status: 404 });
      }),
    );

    render(<App />);

    expect(await screen.findByText(/New Walk-in Patient/i)).toBeTruthy();
  });
});
