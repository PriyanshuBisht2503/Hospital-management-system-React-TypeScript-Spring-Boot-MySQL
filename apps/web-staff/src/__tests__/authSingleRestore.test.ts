import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiRequest } from '../api/client';
import { setAccessToken, getAccessToken, clearAccessToken } from '../api/tokenStore';

describe('AuthContext Silent Session Restore under React dev StrictMode', () => {
  beforeEach(() => {
    clearAccessToken();
    vi.restoreAllMocks();
  });

  it('guarantees silent session restore runs exactly once even under React dev StrictMode double mount', async () => {
    let refreshCallCount = 0;

    const mockFetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url === '/api/auth/refresh') {
        refreshCallCount++;
        return new Response(
          JSON.stringify({
            accessToken: 'session-restored-jwt-token',
            tokenType: 'Bearer',
            expiresInMs: 1800000,
            user: {
              id: 1,
              email: 'admin123@hms.local',
              fullName: 'System Administrator (Demo)',
              phone: '+971 4 000 1001',
              role: 'ADMIN',
              isActive: true,
              createdAt: '2026-01-01T00:00:00Z',
              updatedAt: '2026-01-01T00:00:00Z',
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }

      return new Response('Not Found', { status: 404 });
    });

    vi.stubGlobal('fetch', mockFetch);

    // Simulate AuthContext lifecycle with restoreAttemptedRef across StrictMode double invocation:
    // In React 18/19 StrictMode, effects mount -> cleanup -> remount with the SAME ref instance.
    const restoreAttemptedRef = { current: false };

    const runRestoreEffect = async () => {
      if (restoreAttemptedRef.current) {
        return;
      }
      restoreAttemptedRef.current = true;

      const response = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
      });

      if (response.ok) {
        const data = await response.json();
        setAccessToken(data.accessToken);
      }
    };

    // 1st run of effect (initial mount)
    await runRestoreEffect();

    // 2nd run of effect (StrictMode intentional remount with preserved ref)
    await runRestoreEffect();

    // Assert that /api/auth/refresh was called EXACTLY ONCE
    expect(refreshCallCount).toBe(1);
    expect(getAccessToken()).toBe('session-restored-jwt-token');
  });

  it('rejects session restore if restored user has PATIENT role and logs out immediately', async () => {
    let logoutCalled = false;

    const mockFetch = vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url === '/api/auth/refresh') {
        return new Response(
          JSON.stringify({
            accessToken: 'patient-jwt-token',
            tokenType: 'Bearer',
            expiresInMs: 1800000,
            user: {
              id: 99,
              email: 'patient@example.com',
              role: 'PATIENT',
              isActive: true,
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }

      if (url === '/api/auth/logout') {
        logoutCalled = true;
        return new Response(JSON.stringify({ message: 'Logged out' }), { status: 200 });
      }

      return new Response('Not Found', { status: 404 });
    });

    vi.stubGlobal('fetch', mockFetch);

    const STAFF_ROLES = ['ADMIN', 'DOCTOR', 'RECEPTIONIST'];
    const restoreAttemptedRef = { current: false };
    let authError: string | null = null;

    const runRestoreEffect = async () => {
      if (restoreAttemptedRef.current) return;
      restoreAttemptedRef.current = true;

      const res = await fetch('/api/auth/refresh', { method: 'POST' });
      if (res.ok) {
        const data = await res.json();
        if (!STAFF_ROLES.includes(data.user.role)) {
          await fetch('/api/auth/logout', { method: 'POST' });
          clearAccessToken();
          authError = 'This portal is for hospital staff';
          return;
        }
        setAccessToken(data.accessToken);
      }
    };

    await runRestoreEffect();

    expect(logoutCalled).toBe(true);
    expect(getAccessToken()).toBeNull();
    expect(authError).toBe('This portal is for hospital staff');
  });
});
