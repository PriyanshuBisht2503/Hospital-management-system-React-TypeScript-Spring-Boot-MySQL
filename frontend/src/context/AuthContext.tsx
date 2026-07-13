import React, { createContext, useState, useEffect, useContext } from 'react';
import axios from 'axios';
import { api, setAccessToken } from '../services/api';
import { setCookie, getCookie, eraseCookie } from '../utils/cookie';

const API_BASE_URL = (import.meta.env.VITE_API_URL || '') + '/api/v1';

export interface User {
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
  permissions: string[];
}

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  register: (payload: any) => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const recoverSession = async () => {
      const storedUser = localStorage.getItem('user');
      const refreshToken = getCookie('refreshToken');

      if (storedUser && refreshToken) {
        try {
          // Perform silent token refresh immediately using base Axios (avoiding interceptors)
          const response = await axios.post(`${API_BASE_URL}/auth/refresh`, { refreshToken });
          const { accessToken: newAccessToken, refreshToken: newRefreshToken } = response.data;

          setAccessToken(newAccessToken);
          if (newRefreshToken) {
            setCookie('refreshToken', newRefreshToken, 7);
          }
          setUser(JSON.parse(storedUser));
        } catch (e) {
          // Refresh failed: session expired or invalid. Clean up
          setAccessToken(null);
          eraseCookie('refreshToken');
          localStorage.removeItem('user');
          setUser(null);
        }
      } else {
        // No session to recover, clear details to preserve consistency
        setAccessToken(null);
        eraseCookie('refreshToken');
        localStorage.removeItem('user');
        setUser(null);
      }
      setLoading(false);
    };

    recoverSession();

    const handleLogoutEvent = () => {
      logout();
    };
    window.addEventListener('auth-logout', handleLogoutEvent);
    return () => {
      window.removeEventListener('auth-logout', handleLogoutEvent);
    };
  }, []);

  const login = async (email: string, password: string) => {
    const response = await api.post('/auth/login', { email, password });
    const { accessToken, refreshToken, email: resEmail, firstName, lastName, roles, permissions } = response.data;

    const userProfile: User = { email: resEmail, firstName, lastName, roles, permissions };

    setAccessToken(accessToken);
    setCookie('refreshToken', refreshToken, 7);
    localStorage.setItem('user', JSON.stringify(userProfile));

    setUser(userProfile);
  };

  const logout = () => {
    // Call API logout endpoint (automatically uses interceptor Bearer token)
    api.post('/auth/logout', {}).catch(() => {});
    
    setAccessToken(null);
    eraseCookie('refreshToken');
    localStorage.removeItem('user');
    setUser(null);
  };

  const register = async (payload: any) => {
    await api.post('/auth/register', payload);
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, loading, login, logout, register }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
