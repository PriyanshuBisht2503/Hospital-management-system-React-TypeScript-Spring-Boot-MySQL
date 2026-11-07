import { describe, it, expect, vi, beforeEach } from 'vitest';
import { listPatients } from '../api/patients';

describe('patients API: search + pagination query params', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  const emptyPage = () =>
    new Response(
      JSON.stringify({ content: [], totalElements: 0, totalPages: 0, pageNumber: 0, pageSize: 6, isFirst: true, isLast: true }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    );

  it('builds the right query string for search, page and size', async () => {
    const urls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        urls.push(typeof input === 'string' ? input : input.toString());
        return emptyPage();
      }),
    );

    await listPatients({ search: 'Ali', page: 2, size: 6 });

    expect(urls[0]).toBe('/api/patients?search=Ali&page=2&size=6');
  });

  it('omits an empty search term and keeps the page and size', async () => {
    const urls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        urls.push(typeof input === 'string' ? input : input.toString());
        return emptyPage();
      }),
    );

    await listPatients({ search: '', page: 0, size: 6 });

    expect(urls[0]).toBe('/api/patients?page=0&size=6');
  });
});
