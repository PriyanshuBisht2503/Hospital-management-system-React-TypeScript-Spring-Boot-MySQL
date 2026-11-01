import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiRequest } from '../api/client';
import { setAccessToken, getAccessToken, clearAccessToken } from '../api/tokenStore';

describe('Single-Flight Token Refresh', () => {
  beforeEach(() => {
    clearAccessToken();
    vi.restoreAllMocks();
  });

  it('coalesces multiple concurrent 401 requests into exactly ONE /api/auth/refresh call and retries all original requests', async () => {
    setAccessToken('expired-token');

    let refreshCallCount = 0;
    let protectedCallCount = 0;

    const mockFetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url === '/api/auth/refresh') {
        refreshCallCount++;
        // Small delay to simulate network latency and ensure concurrency overlap
        await new Promise((resolve) => setTimeout(resolve, 50));
        return new Response(
          JSON.stringify({
            accessToken: 'refreshed-new-access-token',
            tokenType: 'Bearer',
            expiresInMs: 1800000,
            user: {
              id: 1,
              email: 'staff@aster.ae',
              fullName: 'Staff Member',
              phone: '+971500000000',
              role: 'ADMIN',
              isActive: true,
              createdAt: '2026-01-01T00:00:00Z',
              updatedAt: '2026-01-01T00:00:00Z',
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }

      if (url === '/api/admin/users') {
        protectedCallCount++;
        const authHeader = (init?.headers as Headers)?.get('Authorization');

        if (authHeader === 'Bearer refreshed-new-access-token') {
          return new Response(JSON.stringify({ content: [], totalElements: 0 }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
        }

        // Initially returns 401 Unauthorized
        return new Response(
          JSON.stringify({
            status: 401,
            error: 'Unauthorized',
            message: 'Token has expired',
            path: '/api/admin/users',
            timestamp: new Date().toISOString(),
          }),
          { status: 401, headers: { 'Content-Type': 'application/json' } }
        );
      }

      return new Response('Not Found', { status: 404 });
    });

    vi.stubGlobal('fetch', mockFetch);

    // Launch 3 simultaneous requests that all hit 401
    const [res1, res2, res3] = await Promise.all([
      apiRequest<{ content: unknown[] }>('/api/admin/users'),
      apiRequest<{ content: unknown[] }>('/api/admin/users'),
      apiRequest<{ content: unknown[] }>('/api/admin/users'),
    ]);

    // Assert that all 3 succeeded after retry
    expect(res1).toEqual({ content: [], totalElements: 0 });
    expect(res2).toEqual({ content: [], totalElements: 0 });
    expect(res3).toEqual({ content: [], totalElements: 0 });

    // Assert that exactly ONE refresh request was made despite 3 simultaneous 401s
    expect(refreshCallCount).toBe(1);

    // Assert that the token store was updated to the new token
    expect(getAccessToken()).toBe('refreshed-new-access-token');

    // Total calls to /api/admin/users: 3 initial (all returned 401) + 3 retried (all succeeded) = 6
    expect(protectedCallCount).toBe(6);
  });
});

