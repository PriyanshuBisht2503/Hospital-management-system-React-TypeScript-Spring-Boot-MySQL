import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from '@hms/ui';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { BranchesPage } from './pages/BranchesPage';
import { DepartmentsPage } from './pages/DepartmentsPage';
import { SpecialtiesPage } from './pages/SpecialtiesPage';
import { RoomsPage } from './pages/RoomsPage';
import { UsersPage } from './pages/UsersPage';
import { DoctorsPage } from './pages/DoctorsPage';
import { AuditLogsPage } from './pages/AuditLogsPage';
import { PatientsPage } from './pages/PatientsPage';
import { NewPatientPage } from './pages/NewPatientPage';
import { PatientDetailPage } from './pages/PatientDetailPage';
import { SchedulesPage } from './pages/SchedulesPage';
import { DoctorSchedulePage } from './pages/DoctorSchedulePage';
import { PATIENT_DIRECTORY_ROLES, PATIENT_CREATE_ROLES, SCHEDULE_ROLES } from './lib/access';
import { ProtectedRoute } from './components/ProtectedRoute';
import { AppLayout } from './components/AppLayout';
import { ForbiddenPage } from './pages/ForbiddenPage';
import { NotFoundPage } from './pages/NotFoundPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ToastProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/login" element={<LoginPage />} />

              {/* Protected Staff Routes */}
              <Route element={<ProtectedRoute />}>
                <Route element={<AppLayout />}>
                  <Route path="/" element={<Navigate to="/dashboard" replace />} />
                  <Route path="/dashboard" element={<DashboardPage />} />
                </Route>
              </Route>

              {/* Patients (Phase 2): directory + profile for staff; create for ADMIN/RECEPTIONIST */}
              <Route element={<ProtectedRoute allowedRoles={PATIENT_CREATE_ROLES} />}>
                <Route element={<AppLayout />}>
                  <Route path="/patients/new" element={<NewPatientPage />} />
                </Route>
              </Route>
              <Route element={<ProtectedRoute allowedRoles={PATIENT_DIRECTORY_ROLES} />}>
                <Route element={<AppLayout />}>
                  <Route path="/patients" element={<PatientsPage />} />
                  <Route path="/patients/:id" element={<PatientDetailPage />} />
                </Route>
              </Route>

              {/* Schedules (Phase 2): list + per-doctor templates/exceptions */}
              <Route element={<ProtectedRoute allowedRoles={SCHEDULE_ROLES} />}>
                <Route element={<AppLayout />}>
                  <Route path="/schedules" element={<SchedulesPage />} />
                  <Route path="/schedules/:doctorId" element={<DoctorSchedulePage />} />
                </Route>
              </Route>

              {/* Admin-only Master Data & Management Routes */}
              <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
                <Route element={<AppLayout />}>
                  <Route path="/users" element={<UsersPage />} />
                  <Route path="/branches" element={<BranchesPage />} />
                  <Route path="/departments" element={<DepartmentsPage />} />
                  <Route path="/specialties" element={<SpecialtiesPage />} />
                  <Route path="/rooms" element={<RoomsPage />} />
                  <Route path="/doctors" element={<DoctorsPage />} />
                  <Route path="/audit-logs" element={<AuditLogsPage />} />
                </Route>
              </Route>

              <Route path="/403" element={<ForbiddenPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </BrowserRouter>
        </ToastProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
}
