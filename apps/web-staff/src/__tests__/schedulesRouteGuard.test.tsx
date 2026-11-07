// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import App from '../App';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('schedules: route guard', () => {
  it('a RECEPTIONIST can open /schedules via the real App routes', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      const json = (b: unknown) => new Response(JSON.stringify(b), { status: 200, headers: { 'Content-Type': 'application/json' } });
      if (url === '/api/auth/refresh') {
        return json({ accessToken: 't', tokenType: 'Bearer', expiresIn: 1800, user: { id: 3, email: 'r@x', phone: null, role: 'RECEPTIONIST', fullName: 'Rec', enabled: true, doctorId: null } });
      }
      if (url.startsWith('/api/doctors/search')) {
        return json({ content: [], pageNumber: 0, pageSize: 6, totalElements: 0, totalPages: 0, isFirst: true, isLast: true });
      }
      return new Response('Not Found', { status: 404 });
    }));

    window.history.pushState({}, '', '/schedules');
    render(<App />);

    expect(await screen.findByText(/Doctor Schedules/i)).toBeTruthy();
  });
});
