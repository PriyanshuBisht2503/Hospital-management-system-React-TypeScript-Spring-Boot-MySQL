// @vitest-environment jsdom
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '@hms/ui';
import { UsersPage } from '../pages/UsersPage';
import { DoctorsPage } from '../pages/DoctorsPage';
import { BranchesPage } from '../pages/BranchesPage';
import { DepartmentsPage } from '../pages/DepartmentsPage';
import { SpecialtiesPage } from '../pages/SpecialtiesPage';
import { RoomsPage } from '../pages/RoomsPage';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const emptyPage = () => ({
  content: [],
  pageNumber: 0,
  pageSize: 6,
  totalElements: 0,
  totalPages: 0,
  isFirst: true,
  isLast: true,
});

const fieldErrors = (fieldErrors: Record<string, string>) =>
  json({ status: 400, error: 'Validation Failed', message: 'Validation failed', path: '/api', timestamp: new Date().toISOString(), fieldErrors }, 400);

function renderPage(children: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>{children}</MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('mutation rejections are handled (no unhandled rejection)', () => {
  it('UsersPage create staff: maps email field error', async () => {
    vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'POST') return fieldErrors({ email: 'Email already registered' });
      return json(emptyPage());
    }));

    renderPage(<UsersPage />);
    await screen.findByText(/No users found/i);

    fireEvent.click(screen.getByRole('button', { name: /Create Staff Member/i }));
    fireEvent.change(screen.getByLabelText(/Full Name/i), { target: { value: 'John Doe' } });
    fireEvent.change(screen.getByLabelText(/Staff Email Address/i), { target: { value: 'taken@example.com' } });
    fireEvent.change(screen.getByLabelText(/Phone Number/i), { target: { value: '+971501234567' } });
    fireEvent.change(screen.getByLabelText(/Initial Password/i), { target: { value: 'Password123' } });

    fireEvent.click(screen.getAllByRole('button', { name: /Create Staff Member/i })[1]);

    expect(await screen.findByText('Email already registered')).toBeTruthy();
  });

  it('DoctorsPage create doctor: maps email field error', async () => {
    vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'POST') return fieldErrors({ email: 'Email already registered' });
      return json(emptyPage());
    }));

    renderPage(<DoctorsPage />);
    await screen.findByText(/No doctor profiles found/i);

    fireEvent.click(screen.getByRole('button', { name: /Create Doctor/i }));
    fireEvent.change(screen.getByLabelText(/Doctor Full Name/i), { target: { value: 'Dr. John Smith' } });
    fireEvent.change(screen.getByLabelText(/Staff Email Address/i), { target: { value: 'taken@example.com' } });
    fireEvent.change(screen.getByLabelText(/Phone Number/i), { target: { value: '+971501234567' } });
    fireEvent.change(screen.getByLabelText(/Initial Password/i), { target: { value: 'Password123' } });
    fireEvent.change(screen.getByLabelText(/Consultation Fee/i), { target: { value: '300' } });
    fireEvent.change(screen.getByLabelText(/Years of Experience/i), { target: { value: '10' } });

    fireEvent.click(screen.getByRole('button', { name: /Create Doctor & Profile/i }));

    expect(await screen.findByText('Email already registered')).toBeTruthy();
  });

  it('BranchesPage create branch: maps code field error', async () => {
    vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'POST') return fieldErrors({ code: 'Code already exists' });
      return json(emptyPage());
    }));

    renderPage(<BranchesPage />);
    await screen.findByText(/No branches found/i);

    fireEvent.click(screen.getByRole('button', { name: /Add Branch/i }));
    fireEvent.change(screen.getByLabelText(/Branch Code/i), { target: { value: 'BR01' } });
    fireEvent.change(screen.getByLabelText(/Branch Name/i), { target: { value: 'Test Branch' } });
    fireEvent.change(screen.getByLabelText(/City/i), { target: { value: 'Dubai' } });

    fireEvent.click(screen.getByRole('button', { name: /Create Branch/i }));

    expect(await screen.findByText('Code already exists')).toBeTruthy();
  });

  it('DepartmentsPage create department: maps code field error', async () => {
    vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'POST') return fieldErrors({ code: 'Code already exists' });
      return json(emptyPage());
    }));

    renderPage(<DepartmentsPage />);
    await screen.findByText(/No departments found/i);

    fireEvent.click(screen.getByRole('button', { name: /Add Department/i }));
    fireEvent.change(screen.getByLabelText(/Department Code/i), { target: { value: 'DEPT01' } });
    fireEvent.change(screen.getByLabelText(/Department Name/i), { target: { value: 'Test Department' } });

    fireEvent.click(screen.getByRole('button', { name: /Create Department/i }));

    expect(await screen.findByText('Code already exists')).toBeTruthy();
  });

  it('SpecialtiesPage create specialty: maps code field error', async () => {
    vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'POST') return fieldErrors({ code: 'Code already exists' });
      return json(emptyPage());
    }));

    renderPage(<SpecialtiesPage />);
    await screen.findByText(/No specialties found/i);

    fireEvent.click(screen.getByRole('button', { name: /Add Specialty/i }));
    fireEvent.change(screen.getByLabelText(/Specialty Code/i), { target: { value: 'SPEC01' } });
    fireEvent.change(screen.getByLabelText(/Specialty Name/i), { target: { value: 'Test Specialty' } });

    fireEvent.click(screen.getByRole('button', { name: /Create Specialty/i }));

    expect(await screen.findByText('Code already exists')).toBeTruthy();
  });

  it('RoomsPage create room: maps roomNumber field error', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (init?.method === 'POST') return fieldErrors({ roomNumber: 'Room number already exists' });
      if (url.startsWith('/api/branches')) {
        return json({ ...emptyPage(), content: [{ id: 1, code: 'BR01', name: 'Main', city: 'Dubai', isActive: true }] });
      }
      if (url.startsWith('/api/departments')) {
        return json({ ...emptyPage(), content: [{ id: 1, code: 'D01', name: 'Cardiology', isActive: true }] });
      }
      return json(emptyPage());
    }));

    renderPage(<RoomsPage />);
    await screen.findByText(/No rooms found/i);

    fireEvent.click(screen.getByRole('button', { name: /Add Room/i }));
    fireEvent.change(screen.getByLabelText(/Hospital Branch/i), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText(/^Department$/i), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText(/Room Number/i), { target: { value: '101-A' } });
    fireEvent.change(screen.getByLabelText(/Room Type/i), { target: { value: 'CONSULTATION' } });

    fireEvent.click(screen.getByRole('button', { name: /Create Room/i }));

    expect(await screen.findByText('Room number already exists')).toBeTruthy();
  });
});
