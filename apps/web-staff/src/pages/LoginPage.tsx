import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useLocation } from 'react-router-dom';
import { z } from 'zod';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../types/auth';
import { Button, Input, Card } from '@hms/ui';

export const loginSchema = z.object({
  email: z.string().min(1, 'Email is required').email('Please enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

export type LoginFormData = z.infer<typeof loginSchema>;

export const LoginPage: React.FC = () => {
  const { login, isAuthenticated, authError, clearError } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [isLoading, setIsLoading] = useState(false);
  const [rateLimitSeconds, setRateLimitSeconds] = useState<number | null>(null);
  const [genericError, setGenericError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<LoginFormData>({
    defaultValues: {
      email: '',
      password: '',
    },
  });

  // Countdown timer for 429 Rate Limit
  useEffect(() => {
    if (rateLimitSeconds === null || rateLimitSeconds <= 0) return;

    const timer = setInterval(() => {
      setRateLimitSeconds((prev) => {
        if (prev === null || prev <= 1) {
          clearInterval(timer);
          return null;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [rateLimitSeconds]);

  // If already authenticated, redirect to destination or dashboard
  useEffect(() => {
    if (isAuthenticated) {
      const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/dashboard';
      navigate(from, { replace: true });
    }
  }, [isAuthenticated, navigate, location]);

  const onSubmit = async (data: LoginFormData) => {
    clearError();
    setGenericError(null);

    // 1. Zod client-side validation
    const validationResult = loginSchema.safeParse(data);
    if (!validationResult.success) {
      for (const issue of validationResult.error.issues) {
        const fieldName = issue.path[0] as keyof LoginFormData;
        setError(fieldName, { message: issue.message });
      }
      return;
    }

    setIsLoading(true);

    try {
      await login(data.email, data.password);
      // Navigation is triggered by the useEffect on isAuthenticated
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.status === 429 && err.retryAfterSeconds) {
          setRateLimitSeconds(err.retryAfterSeconds);
          setGenericError(`Too many login attempts. Please wait ${err.retryAfterSeconds} seconds before trying again.`);
        } else if (err.fieldErrors) {
          // Map backend field errors to form fields
          Object.entries(err.fieldErrors).forEach(([field, msg]) => {
            setError(field as keyof LoginFormData, { message: msg });
          });
        } else {
          setGenericError(err.message || 'Invalid email or password');
        }
      } else if (err instanceof Error) {
        setGenericError(err.message);
      } else {
        setGenericError('An unexpected authentication error occurred.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const displayError = authError || genericError;

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#f1f5f9',
        padding: '24px',
        fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
      }}
    >
      <div style={{ width: '100%', maxWidth: '420px' }}>
        <div style={{ textAlign: 'center', marginBottom: '28px' }}>
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '48px',
              height: '48px',
              borderRadius: '12px',
              backgroundColor: '#0d9488',
              color: '#ffffff',
              fontSize: '24px',
              fontWeight: 700,
              marginBottom: '12px',
            }}
          >
            H
          </div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', margin: 0 }}>
            Hospital Staff Portal
          </h1>
          <p style={{ fontSize: '14px', color: '#64748b', marginTop: '6px', marginBottom: 0 }}>
            Authorized Aster Hospital Staff Access Only
          </p>
        </div>

        <Card>
          <form onSubmit={handleSubmit(onSubmit)} noValidate style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {displayError && (
              <div
                role="alert"
                style={{
                  padding: '12px 14px',
                  borderRadius: '8px',
                  backgroundColor: '#fef2f2',
                  border: '1px solid #fecaca',
                  color: '#991b1b',
                  fontSize: '13px',
                  fontWeight: 500,
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: '8px',
                }}
              >
                <span>{displayError}</span>
              </div>
            )}

            {rateLimitSeconds !== null && rateLimitSeconds > 0 && (
              <div
                role="status"
                style={{
                  padding: '10px 14px',
                  borderRadius: '8px',
                  backgroundColor: '#fffbeb',
                  border: '1px solid #fde68a',
                  color: '#92400e',
                  fontSize: '13px',
                  fontWeight: 600,
                  textAlign: 'center',
                }}
              >
                Rate limit active: retry in {rateLimitSeconds}s
              </div>
            )}

            <div>
              <Input
                label="Staff Email Address"
                type="email"
                placeholder="doctor@aster.ae"
                autoComplete="email"
                error={errors.email?.message}
                {...register('email')}
              />
            </div>

            <div>
              <Input
                label="Password"
                type="password"
                placeholder="••••••••"
                autoComplete="current-password"
                error={errors.password?.message}
                {...register('password')}
              />
            </div>

            <Button
              type="submit"
              variant="primary"
              size="md"
              disabled={isLoading || (rateLimitSeconds !== null && rateLimitSeconds > 0)}
              style={{
                width: '100%',
                marginTop: '8px',
                opacity: isLoading || (rateLimitSeconds !== null && rateLimitSeconds > 0) ? 0.7 : 1,
                cursor: isLoading || (rateLimitSeconds !== null && rateLimitSeconds > 0) ? 'not-allowed' : 'pointer',
              }}
            >
              {isLoading ? 'Authenticating...' : 'Sign In'}
            </Button>
          </form>
        </Card>

        <p style={{ textAlign: 'center', fontSize: '12px', color: '#94a3b8', marginTop: '24px' }}>
          Restricted system. Access logged and monitored for compliance.
        </p>
      </div>
    </div>
  );
};

