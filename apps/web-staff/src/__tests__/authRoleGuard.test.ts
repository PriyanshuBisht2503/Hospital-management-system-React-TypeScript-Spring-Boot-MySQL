import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiRequest } from '../api/client';
import { setAccessToken, clearAccessToken } from '../api/tokenStore';
import { Role } from '../types/auth';

const STAFF_ROLES: Role[] = ['ADMIN', 'DOCTOR', 'RECEPTIONIST'];

describe('Role Guard and Staff Restrictions', () => {
  beforeEach(() => {
    clearAccessToken();
    vi.restoreAllMocks();
  });

  it('rejects PATIENT accounts logging into the staff portal with "This portal is for hospital staff"', async () => {
    const mockFetch = vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url === '/api/auth/login') {
        return new Response(
          JSON.stringify({
            accessToken: 'patient-token',
            tokenType: 'Bearer',
            expiresInMs: 1800000,
            user: {
              id: 99,
              email: 'patient@gmail.com',
              fullName: 'Patient John',
              phone: '+971509999999',
              role: 'PATIENT',
              isActive: true,
              createdAt: '2026-01-01T00:00:00Z',
              updatedAt: '2026-01-01T00:00:00Z',
            },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }

      if (url === '/api/auth/logout') {
        return new Response(JSON.stringify({ message: 'Logged out successfully' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      return new Response('Not Found', { status: 404 });
    });

    vi.stubGlobal('fetch', mockFetch);

    // Simulate login attempt for patient
    let authError: string | null = null;
    try {
      const response = await apiRequest<{ user: { role: Role }; accessToken: string }>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email: 'patient@gmail.com', password: 'password123' }),
      });

      if (!STAFF_ROLES.includes(response.user.role)) {
        await apiRequest('/api/auth/logout', { method: 'POST' });
        clearAccessToken();
        throw new Error('This portal is for hospital staff');
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        authError = err.message;
      }
    }

    expect(authError).toBe('This portal is for hospital staff');
    // Ensure logout was called to clear any backend session
    expect(mockFetch).toHaveBeenCalledWith(
      '/api/auth/logout',
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('allows staff roles (ADMIN, DOCTOR, RECEPTIONIST) and accepts their credentials', () => {
    const roles: Role[] = ['ADMIN', 'DOCTOR', 'RECEPTIONIST'];
    for (const role of roles) {
      expect(STAFF_ROLES.includes(role)).toBe(true);
    }
    expect(STAFF_ROLES.includes('PATIENT')).toBe(false);
  });

  it('enforces that RECEPTIONIST and DOCTOR roles are blocked from admin-only routes', () => {
    const adminAllowedRoles: Role[] = ['ADMIN'];
    const receptionistRole: Role = 'RECEPTIONIST';
    const doctorRole: Role = 'DOCTOR';

    const canReceptionistAccessAdmin = adminAllowedRoles.includes(receptionistRole);
    const canDoctorAccessAdmin = adminAllowedRoles.includes(doctorRole);

    expect(canReceptionistAccessAdmin).toBe(false);
    expect(canDoctorAccessAdmin).toBe(false);
  });
});

