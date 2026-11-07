/**
 * In-memory Access Token Store.
 * 
 * Strict Security Requirement:
 * Access token is kept in memory only.
 * NEVER use localStorage or sessionStorage for tokens.
 */

let memoryAccessToken: string | null = null;

export const getAccessToken = (): string | null => {
  return memoryAccessToken;
};

export const setAccessToken = (token: string | null): void => {
  memoryAccessToken = token;
};

export const clearAccessToken = (): void => {
  memoryAccessToken = null;
};

