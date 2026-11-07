import { describe, it, expect, beforeEach } from 'vitest';
import { getAccessToken, setAccessToken, clearAccessToken } from '../api/tokenStore';

describe('In-Memory Token Storage Security', () => {
  beforeEach(() => {
    clearAccessToken();
    if (typeof localStorage !== 'undefined') {
      localStorage.clear();
    }
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.clear();
    }
  });

  it('stores and retrieves access token strictly in memory', () => {
    expect(getAccessToken()).toBeNull();

    setAccessToken('test-jwt-token-xyz');
    expect(getAccessToken()).toBe('test-jwt-token-xyz');

    clearAccessToken();
    expect(getAccessToken()).toBeNull();
  });

  it('never touches or writes tokens to localStorage or sessionStorage', () => {
    setAccessToken('secret-token-123');

    if (typeof localStorage !== 'undefined') {
      expect(localStorage.getItem('token')).toBeNull();
      expect(localStorage.getItem('accessToken')).toBeNull();
      expect(localStorage.getItem('jwt')).toBeNull();
      expect(Object.keys(localStorage).length).toBe(0);
    }

    if (typeof sessionStorage !== 'undefined') {
      expect(sessionStorage.getItem('token')).toBeNull();
      expect(sessionStorage.getItem('accessToken')).toBeNull();
      expect(sessionStorage.getItem('jwt')).toBeNull();
      expect(Object.keys(sessionStorage).length).toBe(0);
    }
  });
});

