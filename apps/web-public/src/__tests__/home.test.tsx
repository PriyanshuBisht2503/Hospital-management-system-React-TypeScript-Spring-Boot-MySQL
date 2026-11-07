// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { HomePage } from '../pages/HomePage';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const emptyPage = { content: [], pageNumber: 0, pageSize: 10, totalElements: 0, totalPages: 0, isFirst: true, isLast: true };

describe('HomePage', () => {
  it('navigates to /doctors with the search query', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/specialties')) {
        return json({ ...emptyPage, content: [{ id: 1, name: 'Cardiology', code: 'CARD', description: null, isActive: true }] });
      }
      if (url.startsWith('/api/branches')) return json(emptyPage);
      return json(emptyPage);
    }));

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/doctors" element={<div>Doctors page</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const input = await screen.findByLabelText('Doctor name');
    fireEvent.change(input, { target: { value: 'Sarah' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search doctors' }));

    expect(await screen.findByText('Doctors page')).toBeTruthy();
  });
});
