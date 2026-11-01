import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { User, Role, AuthResponse, ApiError } from '../types/auth';
import { apiRequest, setSessionExpiredHandler, refreshAuthToken } from '../api/client';
import { setAccessToken, clearAccessToken } from '../api/tokenStore';

const STAFF_ROLES: Role[] = ['ADMIN', 'DOCTOR', 'RECEPTIONIST'];

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  authError: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  clearError: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [authError, setAuthError] = useState<string | null>(null);

  const clearError = useCallback(() => {
    setAuthError(null);
  }, []);

  const handleLogout = useCallback(async () => {
    try {
      await apiRequest('/api/auth/logout', { method: 'POST', skipAuth: true });
    } catch {
      // Proceed with local cleanup regardless
    } finally {
      clearAccessToken();
      setUser(null);
      setAuthError(null);
    }
  }, []);

  // Set up session expired callback from client.ts
  useEffect(() => {
    setSessionExpiredHandler(() => {
      setUser(null);
    });

    return () => {
      setSessionExpiredHandler(null);
    };
  }, []);

  // Silent session restoration on app load
  useEffect(() => {
    let isMounted = true;

    const restoreSession = async () => {
      try {
        const response = await fetch('/api/auth/refresh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
        });

        if (response.ok) {
          const data: AuthResponse = await response.json();
          if (!STAFF_ROLES.includes(data.user.role)) {
            // Patient attempted session restore on staff portal
            await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' }).catch(() => {});
            clearAccessToken();
            if (isMounted) {
              setUser(null);
              setAuthError('This portal is for hospital staff');
            }
            return;
          }

          setAccessToken(data.accessToken);
          if (isMounted) {
            setUser(data.user);
          }
        } else {
          // Unauthenticated or expired refresh token
          clearAccessToken();
          if (isMounted) {
            setUser(null);
          }
        }
      } catch (err) {
        // Network error: do not log the user out
        console.warn('Network issue while restoring session:', err);
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    restoreSession();

    return () => {
      isMounted = false;
    };
  }, []);

  const login = async (email: string, password: string): Promise<void> => {
    setAuthError(null);
    try {
      const response = await apiRequest<AuthResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
        skipAuth: true,
      });

      if (!STAFF_ROLES.includes(response.user.role)) {
        // Reject PATIENT role
        await handleLogout();
        const errMessage = 'This portal is for hospital staff';
        setAuthError(errMessage);
        throw new Error(errMessage);
      }

      setAccessToken(response.accessToken);
      setUser(response.user);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setAuthError(err.message);
        throw err;
      }
      if (err instanceof Error) {
        setAuthError(err.message);
        throw err;
      }
      const genericMsg = 'Authentication failed. Please check your credentials.';
      setAuthError(genericMsg);
      throw new Error(genericMsg);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        authError,
        login,
        logout: handleLogout,
        clearError,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

