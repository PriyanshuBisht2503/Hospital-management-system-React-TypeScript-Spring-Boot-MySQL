// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import App from '../App';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const json = (b: unknown) => new Response(JSON.stringify(b), { status: 200, headers: { 'Content-Type': 'application/json' } });
const emptyPage = { content: [], pageNumber: 0, pageSize: 100, totalElements: 0, totalPages: 0, isFirst: true, isLast: true };

const refresh = (role: string, doctorId: number | null) =>
  json({ accessToken: 't', tokenType: 'Bearer', expiresIn: 1800, user: { id: 1, email: 'x@x', phone: null, role, fullName: 'X', enabled: true, doctorId } });

function renderAt(path: string, role: string, doctorId: number | null = null) {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (url === '/api/auth/refresh') return refresh(role, doctorId);
    if (url.startsWith('/api/appointments')) return json(emptyPage);
    if (url.startsWith('/api/doctors/search')) return json(emptyPage);
    return new Response('Not Found', { status: 404 });
  }));
  window.history.pushState({}, '', path);
  return render(<App />);
}

describe('day view routes', () => {
  it('a DOCTOR can open /my-day', async () => {
    renderAt('/my-day', 'DOCTOR', 9);
    expect(await screen.findByRole('heading', { name: 'My Day' })).toBeTruthy();
  });

  it('a RECEPTIONIST cannot open /my-day', async () => {
    renderAt('/my-day', 'RECEPTIONIST');
    expect(await screen.findByText(/403 - Access Denied/i)).toBeTruthy();
  });

  it('a RECEPTIONIST can open /day-agenda', async () => {
    renderAt('/day-agenda', 'RECEPTIONIST');
    expect(await screen.findByRole('heading', { name: 'Day Agenda' })).toBeTruthy();
  });

  it('a DOCTOR cannot open /day-agenda', async () => {
    renderAt('/day-agenda', 'DOCTOR', 9);
    expect(await screen.findByText(/403 - Access Denied/i)).toBeTruthy();
  });

  it('a PATIENT is blocked from /my-day', async () => {
    renderAt('/my-day', 'PATIENT');
    expect(await screen.findByText(/Sign In/i)).toBeTruthy();
  });
});
