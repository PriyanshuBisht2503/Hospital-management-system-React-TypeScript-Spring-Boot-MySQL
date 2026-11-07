import { ApiError, ApiErrorResponse, AuthResponse } from '../types/auth';
import { getAccessToken, setAccessToken, clearAccessToken } from './tokenStore';

type SessionExpiredHandler = () => void;
let sessionExpiredHandler: SessionExpiredHandler | null = null;

export const setSessionExpiredHandler = (handler: SessionExpiredHandler | null): void => {
  sessionExpiredHandler = handler;
};

// Single-flight refresh mutex
let activeRefreshPromise: Promise<string | null> | null = null;

/**
 * Executes a token refresh call.
 * Guarantees that only ONE refresh request is in flight at any given time.
 */
export const refreshAuthToken = async (): Promise<string | null> => {
  if (activeRefreshPromise) {
    return activeRefreshPromise;
  }

  activeRefreshPromise = (async () => {
    try {
      const response = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include', // sends httpOnly refresh token cookie
      });

      if (!response.ok) {
        if (response.status === 401 || response.status === 403) {
          clearAccessToken();
          if (sessionExpiredHandler) {
            sessionExpiredHandler();
          }
          return null;
        }
        // Other HTTP error during refresh
        const errJson: ApiErrorResponse = await response.json().catch(() => ({
          status: response.status,
          error: response.statusText,
          message: 'Failed to refresh authentication session',
          path: '/api/auth/refresh',
          timestamp: new Date().toISOString(),
        }));
        throw new ApiError(errJson);
      }

      const data: AuthResponse = await response.json();
      setAccessToken(data.accessToken);
      return data.accessToken;
    } catch (error) {
      // If it's a network error (e.g. Failed to fetch), do NOT log the user out
      if (error instanceof TypeError) {
        throw error;
      }
      throw error;
    } finally {
      activeRefreshPromise = null;
    }
  })();

  return activeRefreshPromise;
};

export interface RequestOptions extends RequestInit {
  skipAuth?: boolean;
  retryOn401?: boolean;
}

/**
 * Universal API Request function with automatic auth injection,
 * single-flight refresh queueing, and structured error handling.
 */
export async function apiRequest<T>(
  endpoint: string,
  options: RequestOptions = {}
): Promise<T> {
  const { skipAuth = false, retryOn401 = true, headers: customHeaders, ...restOptions } = options;

  const url = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;

  const requestHeaders = new Headers(customHeaders);
  if (!requestHeaders.has('Content-Type') && !(restOptions.body instanceof FormData)) {
    requestHeaders.set('Content-Type', 'application/json');
  }

  if (!skipAuth) {
    const token = getAccessToken();
    if (token && !requestHeaders.has('Authorization')) {
      requestHeaders.set('Authorization', `Bearer ${token}`);
    }
  }

  let response: Response;
  try {
    response = await fetch(url, {
      ...restOptions,
      headers: requestHeaders,
      credentials: 'include',
    });
  } catch (netError) {
    // Network error: do not log the user out
    throw netError;
  }

  // Handle 401 Unauthorized for protected endpoints
  if (response.status === 401 && retryOn401 && !endpoint.includes('/api/auth/login') && !endpoint.includes('/api/auth/refresh')) {
    try {
      const newAccessToken = await refreshAuthToken();
      if (newAccessToken) {
        // Retry the original request once with new token
        requestHeaders.set('Authorization', `Bearer ${newAccessToken}`);
        return apiRequest<T>(endpoint, {
          ...options,
          headers: requestHeaders,
          retryOn401: false, // Prevent infinite loops
        });
      }
    } catch (refreshErr) {
      if (refreshErr instanceof TypeError) {
        // Network error during refresh: propagate without logging out
        throw refreshErr;
      }
      // Refresh failed with 401
    }
  }

  if (!response.ok) {
    let errorResponse: ApiErrorResponse;
    try {
      errorResponse = await response.json();
    } catch {
      errorResponse = {
        status: response.status,
        error: response.statusText,
        message: response.statusText || 'An error occurred',
        path: url,
        timestamp: new Date().toISOString(),
      };
    }

    let retryAfterSeconds: number | undefined;
    if (response.status === 429) {
      const retryHeader = response.headers.get('Retry-After');
      if (retryHeader) {
        const parsed = parseInt(retryHeader, 10);
        if (!isNaN(parsed)) {
          retryAfterSeconds = parsed;
        }
      }
    }

    throw new ApiError(errorResponse, retryAfterSeconds);
  }

  // Handle 204 No Content
  if (response.status === 204) {
    return {} as T;
  }

  return response.json() as Promise<T>;
}

