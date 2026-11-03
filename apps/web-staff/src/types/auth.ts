export type Role = 'ADMIN' | 'DOCTOR' | 'RECEPTIONIST' | 'PATIENT';

export type StaffRole = 'ADMIN' | 'DOCTOR' | 'RECEPTIONIST';

/**
 * The authenticated user returned by login, refresh, and GET /api/auth/me.
 * Mirrors the backend `UserSummaryDto`, including the Phase 2 `doctorId`
 * field which is populated only for DOCTOR users and null otherwise.
 */
export interface CurrentUser {
  id: number;
  email: string;
  phone: string | null;
  role: Role;
  fullName: string;
  enabled: boolean;
  doctorId: number | null;
}

/** Admin user-list row (mirrors the backend `UserDto`). */
export interface User {
  id: number;
  email: string;
  fullName: string;
  phone: string;
  role: Role;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInMs: number;
  user: CurrentUser;
}

export interface ApiErrorResponse {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
  fieldErrors?: Record<string, string>;
}

export class ApiError extends Error {
  status: number;
  errorTitle: string;
  fieldErrors?: Record<string, string>;
  retryAfterSeconds?: number;

  constructor(response: ApiErrorResponse, retryAfterSeconds?: number) {
    super(response.message);
    this.name = 'ApiError';
    this.status = response.status;
    this.errorTitle = response.error;
    this.fieldErrors = response.fieldErrors;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
