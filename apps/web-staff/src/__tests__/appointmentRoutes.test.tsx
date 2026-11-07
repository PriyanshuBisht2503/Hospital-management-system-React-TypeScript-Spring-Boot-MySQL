// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import App from '../App';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const refresh = (role: string, doctorId: number | null) =>
  new Response(
    JSON.stringify({ accessToken: 't', tokenType: 'Bearer', expiresIn: 1800, user: { id: 1, email: 'x@x', phone: null, role, fullName: 'X', enabled: true, doctorId } }),
    { status: 200, headers: { 'Content-Type': 'application/json' } },
  );

describe('appointments routes', () => {
  it('a DOCTOR cannot open /appointments/new', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url === '/api/auth/refresh') return refresh('DOCTOR', 42);
      return new Response('Not Found', { status: 404 });
    }));
    window.history.pushState({}, '', '/appointments/new');
    render(<App />);
    expect(await screen.findByText(/Access Denied/i)).toBeTruthy();
  });

  it('a RECEPTIONIST can open /appointments/new', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url === '/api/auth/refresh') return refresh('RECEPTIONIST', null);
      return new Response('Not Found', { status: 404 });
    }));
    window.history.pushState({}, '', '/appointments/new');
    render(<App />);
    expect(await screen.findByRole('heading', { name: 'Book Appointment' })).toBeTruthy();
  });
});
