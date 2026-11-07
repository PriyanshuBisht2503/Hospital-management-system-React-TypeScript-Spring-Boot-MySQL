import { describe, it, expect } from 'vitest';
import { loginSchema } from '../pages/LoginPage';
import { ApiError, ApiErrorResponse } from '../types/auth';

describe('Form Validation and Backend Error Mapping', () => {
  describe('Zod Client Validation', () => {
    it('succeeds for valid staff email and password', () => {
      const result = loginSchema.safeParse({
        email: 'doctor@example.com',
        password: 'ValidPassword123!',
      });

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.email).toBe('doctor@example.com');
      }
    });

    it('rejects empty email with "Email is required"', () => {
      const result = loginSchema.safeParse({
        email: '',
        password: 'password123',
      });

      expect(result.success).toBe(false);
      if (!result.success) {
        const emailIssue = result.error.issues.find((i) => i.path[0] === 'email');
        expect(emailIssue).toBeDefined();
        expect(emailIssue?.message).toBe('Email is required');
      }
    });

    it('rejects malformed email with "Please enter a valid email address"', () => {
      const result = loginSchema.safeParse({
        email: 'invalid-email-string',
        password: 'password123',
      });

      expect(result.success).toBe(false);
      if (!result.success) {
        const emailIssue = result.error.issues.find((i) => i.path[0] === 'email');
        expect(emailIssue).toBeDefined();
        expect(emailIssue?.message).toBe('Please enter a valid email address');
      }
    });

    it('rejects empty password with "Password is required"', () => {
      const result = loginSchema.safeParse({
        email: 'doctor@example.com',
        password: '',
      });

      expect(result.success).toBe(false);
      if (!result.success) {
        const passwordIssue = result.error.issues.find((i) => i.path[0] === 'password');
        expect(passwordIssue).toBeDefined();
        expect(passwordIssue?.message).toBe('Password is required');
      }
    });
  });

  describe('Server ErrorResponse Mapping', () => {
    it('maps backend fieldErrors to form fields correctly', () => {
      const backendErrorPayload: ApiErrorResponse = {
        status: 400,
        error: 'Validation Failed',
        message: 'One or more fields failed validation',
        path: '/api/auth/login',
        timestamp: '2026-01-01T00:00:00Z',
        fieldErrors: {
          email: 'Email not found in hospital system',
          password: 'Password does not meet complexity rules',
        },
      };

      const apiError = new ApiError(backendErrorPayload);

      expect(apiError.status).toBe(400);
      expect(apiError.message).toBe('One or more fields failed validation');
      expect(apiError.fieldErrors).toEqual({
        email: 'Email not found in hospital system',
        password: 'Password does not meet complexity rules',
      });
    });

    it('extracts Retry-After header for 429 Rate Limit responses', () => {
      const backend429Payload: ApiErrorResponse = {
        status: 429,
        error: 'Too Many Requests',
        message: 'Too many login attempts. Please try again later.',
        path: '/api/auth/login',
        timestamp: '2026-01-01T00:00:00Z',
      };

      const apiError = new ApiError(backend429Payload, 60);

      expect(apiError.status).toBe(429);
      expect(apiError.retryAfterSeconds).toBe(60);
      expect(apiError.message).toContain('Too many login attempts');
    });
  });
});

