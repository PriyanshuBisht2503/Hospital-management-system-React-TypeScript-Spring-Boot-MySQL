import React, { lazy, Suspense } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Sidebar } from './components/Sidebar';
import { ErrorBoundary } from './components/ErrorBoundary';

// Lazy Loaded Pages for performance bundle split code optimization
const Login = lazy(() => import('./pages/Login').then(m => ({ default: m.Login })));
const Dashboard = lazy(() => import('./pages/Dashboard').then(m => ({ default: m.Dashboard })));
const Patients = lazy(() => import('./pages/Patients').then(m => ({ default: m.Patients })));
const Appointments = lazy(() => import('./pages/Appointments').then(m => ({ default: m.Appointments })));
const Billing = lazy(() => import('./pages/Billing').then(m => ({ default: m.Billing })));

// Enhanced Protected Route with Client-side Role Guards
interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: string[];
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, allowedRoles }) => {
  const { user, isAuthenticated, loading } = useAuth();

  if (loading) {
    return (
      <div style={styles.loadingContainer}>
        <div style={styles.spinner}></div>
        <p>Verifying secure session...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && user && !user.roles.some(role => allowedRoles.includes(role))) {
    // Redirect unauthorized role attempts to home dashboard
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
};

// Authenticated Layout Wrapper
const Layout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  return (
    <div className="app-container">
      <Sidebar />
      <main className="main-content">
        <div className="page-body">{children}</div>
      </main>
    </div>
  );
};

// Standard Loading Fallback for Suspense Code Splitting
const PageLoader: React.FC = () => (
  <div style={styles.loadingContainer}>
    <div style={styles.spinner}></div>
    <p>Loading view...</p>
  </div>
);

function App() {
  return (
    <ErrorBoundary>
      <AuthProvider>
        <Router>
          <Suspense fallback={<PageLoader />}>
            <Routes>
              {/* Public Authentication Path */}
              <Route path="/login" element={<Login />} />

              {/* Secure Outpatient Operations Mappings */}
              <Route
                path="/dashboard"
                element={
                  <ProtectedRoute>
                    <Layout>
                      <Dashboard />
                    </Layout>
                  </ProtectedRoute>
                }
              />
              <Route
                path="/patients"
                element={
                  <ProtectedRoute allowedRoles={['ROLE_ADMIN', 'ROLE_RECEPTIONIST', 'ROLE_DOCTOR']}>
                    <Layout>
                      <Patients />
                    </Layout>
                  </ProtectedRoute>
                }
              />
              <Route
                path="/appointments"
                element={
                  <ProtectedRoute>
                    <Layout>
                      <Appointments />
                    </Layout>
                  </ProtectedRoute>
                }
              />
              <Route
                path="/billing"
                element={
                  <ProtectedRoute>
                    <Layout>
                      <Billing />
                    </Layout>
                  </ProtectedRoute>
                }
              />

              {/* Root Fallback Redirect */}
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </Suspense>
        </Router>
      </AuthProvider>
    </ErrorBoundary>
  );
}

const styles: Record<string, React.CSSProperties> = {
  loadingContainer: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '100vh',
    gap: '1rem',
    color: 'var(--text-muted)',
    fontWeight: 600,
  },
  spinner: {
    width: '40px',
    height: '40px',
    border: '4px solid var(--border-color)',
    borderTop: '4px solid var(--primary)',
    borderRadius: '50%',
    animation: 'spin 1s linear infinite',
  },
};

export default App;
