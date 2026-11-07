// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { DoctorsPage } from '../pages/DoctorsPage';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const emptyPage = { content: [], pageNumber: 0, pageSize: 6, totalElements: 0, totalPages: 0, isFirst: true, isLast: true };

const doctor = {
  id: 7,
  fullName: 'Dr. Sarah Khan',
  bio: 'Cardiologist',
  qualifications: 'MBBS',
  languages: 'English',
  experienceYears: 15,
  photoUrl: null,
  consultationFee: 350,
  specialties: [{ id: 1, name: 'Cardiology', code: 'CARD', description: null, isActive: true }],
  branches: [{ id: 1, name: 'Central Branch', code: 'BRANCH-CENTRAL', address: 'x', city: 'Dubai', phone: null, email: null, isActive: true }],
  isActive: true,
  nextAvailableSlot: '2026-11-10T04:00:00.000Z',
};

function renderDoctors(entry = '/doctors') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/doctors" element={<DoctorsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const baseResponses = (url: string) => {
  if (url.startsWith('/api/specialties')) return json({ ...emptyPage, content: [{ id: 1, name: 'Cardiology', code: 'CARD', description: null, isActive: true }] });
  if (url.startsWith('/api/branches')) return json({ ...emptyPage, content: [{ id: 1, name: 'Central Branch', code: 'BRANCH-CENTRAL', address: 'x', city: 'Dubai', phone: null, email: null, isActive: true }] });
  return null;
};

describe('DoctorsPage', () => {
  it('shows a loading state', () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      const base = baseResponses(url);
      if (base) return base;
      return new Promise<Response>(() => {});
    }));

    renderDoctors();
    expect(screen.getByText('Loading doctors…')).toBeTruthy();
  });

  it('shows the empty state', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      const base = baseResponses(url);
      if (base) return base;
      return json(emptyPage);
    }));

    renderDoctors();
    expect(await screen.findByText('No doctors match your filters.')).toBeTruthy();
  });

  it('shows an error state and retries', async () => {
    let calls = 0;
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      const base = baseResponses(url);
      if (base) return base;
      calls += 1;
      return calls === 1 ? json({ message: 'boom' }, 500) : json({ ...emptyPage, content: [doctor], totalElements: 1, totalPages: 1 });
    }));

    renderDoctors();
    expect(await screen.findByText('Could not load doctors.')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Retry/i }));

    expect(await screen.findByText('Dr. Sarah Khan')).toBeTruthy();
  });

  it('renders doctor cards with the next available IST time', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      const base = baseResponses(url);
      if (base) return base;
      return json({ ...emptyPage, content: [doctor], totalElements: 1, totalPages: 1 });
    }));

    renderDoctors();
    expect(await screen.findByText('Dr. Sarah Khan')).toBeTruthy();
    expect(screen.getByText('Next available: 2026-11-10 09:30 IST')).toBeTruthy();
  });

  it('sends the filter query with page and size', async () => {
    const urls: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/doctors/search')) urls.push(url);
      const base = baseResponses(url);
      if (base) return base;
      return json({ ...emptyPage, content: [doctor], totalElements: 1, totalPages: 1 });
    }));

    renderDoctors('/doctors?specialtyId=1&branchId=2&search=Sarah');
    await screen.findByText('Dr. Sarah Khan');

    await waitFor(() => expect(urls.length).toBeGreaterThan(0));
    const searchUrl = urls[0];
    expect(searchUrl).toContain('specialtyId=1');
    expect(searchUrl).toContain('branchId=2');
    expect(searchUrl).toContain('search=Sarah');
    expect(searchUrl).toContain('size=6');
    expect(searchUrl).toContain('page=0');
  });
});
