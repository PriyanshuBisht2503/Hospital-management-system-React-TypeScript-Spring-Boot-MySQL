import { describe, it, expect } from 'vitest';
import { ApiError, buildQuery } from '../lib/api';

describe('buildQuery', () => {
  it('skips null, undefined and blank values', () => {
    expect(buildQuery({ a: 1, b: null, c: undefined, d: '', e: 0 })).toBe('?a=1&e=0');
  });

  it('encodes values', () => {
    expect(buildQuery({ search: 'A B', page: 2 })).toBe('?search=A+B&page=2');
  });

  it('returns an empty string when there is nothing to send', () => {
    expect(buildQuery({ a: null, b: '' })).toBe('');
  });
});

describe('ApiError', () => {
  it('carries the status and field errors', () => {
    const error = new ApiError(400, 'Validation failed', { fullName: 'required' });
    expect(error).toBeInstanceOf(Error);
    expect(error.status).toBe(400);
    expect(error.message).toBe('Validation failed');
    expect(error.fieldErrors).toEqual({ fullName: 'required' });
  });
});
