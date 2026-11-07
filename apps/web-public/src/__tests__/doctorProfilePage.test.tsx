// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { DoctorProfilePage } from '../pages/DoctorProfilePage';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const doctor = {
  id: 7,
  fullName: 'Dr. Sarah Khan',
  bio: 'Senior cardiologist',
  qualifications: 'MBBS, MD',
  languages: 'English, Arabic',
  experienceYears: 15,
  photoUrl: null,
  consultationFee: 350,
  specialties: [{ id: 1, name: 'Cardiology', code: 'CARD', description: null, isActive: true }],
  branches: [
    { id: 1, name: 'Central Branch', code: 'BRANCH-CENTRAL', address: 'x', city: 'Dubai', phone: null, email: null, isActive: true },
    { id: 2, name: 'North Clinic', code: 'BRANCH-NORTH', address: 'y', city: 'Dubai', phone: null, email: null, isActive: true },
  ],
  isActive: true,
};

function renderProfile(path: string, entry: string) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path={path} element={<DoctorProfilePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('DoctorProfilePage', () => {
  it('renders the doctor and a booking link with doctorId and branchId', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/doctors/7/slots')) return json([]);
      if (url.startsWith('/api/doctors/7')) return json(doctor);
      return json({ message: 'not found' }, 404);
    }));

    renderProfile('/doctors/:id', '/doctors/7');

    expect(await screen.findByRole('heading', { name: 'Dr. Sarah Khan' })).toBeTruthy();
    expect(screen.getByText('Senior cardiologist')).toBeTruthy();

    const book = screen.getByRole('link', { name: 'Book appointment' });
    expect(book.getAttribute('href')).toBe('http://localhost:3001/book?doctorId=7&branchId=1');
  });

  it('shows a friendly not-found page for a 404 doctor', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ message: 'Doctor not found' }, 404)));

    renderProfile('/doctors/:id', '/doctors/99');
    expect(await screen.findByRole('heading', { name: 'Doctor not found' })).toBeTruthy();
  });
});
