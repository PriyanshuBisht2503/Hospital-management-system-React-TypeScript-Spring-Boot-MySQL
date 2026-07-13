import React from 'react';
import { useAuth } from '../context/AuthContext';
import { useDashboardStatsQuery } from '../features/dashboard/useDashboardStats';
import { 
  Users, 
  CalendarRange, 
  CreditCard, 
  Activity, 
  Sparkles, 
  CheckCircle,
  Clock
} from 'lucide-react';
import { Skeleton } from '../components/ui/Skeleton';
import { ErrorBoundaryWrapper } from '../components/ui/ErrorBoundaryWrapper';
import styles from './Dashboard.module.css';

const DashboardContent: React.FC = () => {
  const { user } = useAuth();
  const isPatient = user?.roles.includes('ROLE_PATIENT');

  // Trigger query hook only for staff/doctor roles (patientId=1 fallback default demo mapping)
  const { data: stats, isLoading, isError } = useDashboardStatsQuery(1);

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <div className={styles.headerText}>
          <h1 className={styles.title}>Hello, {user?.firstName}!</h1>
          <p className={styles.subtitle}>Welcome back to CareHMS portal. Here is your overview for today.</p>
        </div>
        <div className={styles.roleBadge}>
          <Sparkles size={16} color="var(--primary)" />
          <span>{user?.roles[0]?.replace('ROLE_', '')} Dashboard</span>
        </div>
      </header>

      {isError && (
        <div className="badge-danger" style={{ display: 'flex', gap: '0.5rem', padding: '0.75rem 1rem', borderRadius: '4px', fontSize: '0.85rem' }}>
          <span>Failed to sync statistics from medical servers. Rendering fallback widgets.</span>
        </div>
      )}

      {isLoading && !isPatient ? (
        <div className={styles.statsGrid}>
          <Skeleton height="110px" />
          <Skeleton height="110px" />
          <Skeleton height="110px" />
        </div>
      ) : !isPatient ? (
        <section className={styles.statsGrid}>
          <div className={`card ${styles.statCard}`}>
            <div className={styles.iconBg} style={{ backgroundColor: 'rgba(9, 133, 107, 0.1)' }}>
              <Users size={24} color="var(--primary)" />
            </div>
            <div>
              <p className={styles.statLabel}>Active Patients</p>
              <h3 className={styles.statVal}>{stats?.patientsCount ?? 38}</h3>
            </div>
          </div>

          <div className={`card ${styles.statCard}`}>
            <div className={styles.iconBg} style={{ backgroundColor: 'var(--info-light)' }}>
              <CalendarRange size={24} color="var(--info)" />
            </div>
            <div>
              <p className={styles.statLabel}>Bookings Scheduled</p>
              <h3 className={styles.statVal}>{stats?.appointmentsCount ?? 14}</h3>
            </div>
          </div>

          <div className={`card ${styles.statCard}`}>
            <div className={styles.iconBg} style={{ backgroundColor: 'var(--warning-light)' }}>
              <CreditCard size={24} color="var(--warning)" />
            </div>
            <div>
              <p className={styles.statLabel}>Pending Invoices</p>
              <h3 className={styles.statVal}>{stats?.invoicesCount ?? 5}</h3>
            </div>
          </div>
        </section>
      ) : (
        <section className={styles.statsGrid}>
          <div className={`card ${styles.statCard}`}>
            <div className={styles.iconBg} style={{ backgroundColor: 'rgba(9, 133, 107, 0.1)' }}>
              <CheckCircle size={24} color="var(--primary)" />
            </div>
            <div>
              <p className={styles.statLabel}>Your Status</p>
              <h3 className={styles.statVal} style={{ fontSize: '1.25rem', color: 'var(--success)' }}>Active Profile</h3>
            </div>
          </div>

          <div className={`card ${styles.statCard}`}>
            <div className={styles.iconBg} style={{ backgroundColor: 'var(--info-light)' }}>
              <Clock size={24} color="var(--info)" />
            </div>
            <div>
              <p className={styles.statLabel}>Total Registrations</p>
              <h3 className={styles.statVal} style={{ fontSize: '1.25rem' }}>Confirmed</h3>
            </div>
          </div>
        </section>
      )}

      <section className={styles.infoSection}>
        <div className={`card ${styles.infoCard}`}>
          <h3 className={styles.infoTitle}>Clinical Operations & Guidelines</h3>
          <p className={styles.infoText}>
            CareHMS is designed to handle electronic health records (EHR) strictly conforming to security standards.
            Always lock patient records before exiting console logs. Maintain absolute compliance with role accesses.
          </p>
          <div className={styles.guidelinesList}>
            <div className={styles.guidelineItem}>
              <Activity size={18} color="var(--primary)" />
              <span>Validate patient insurance cover before generating consultation invoices.</span>
            </div>
            <div className={styles.guidelineItem}>
              <Activity size={18} color="var(--primary)" />
              <span>Confirm doctor slot lock scheduling limits to prevent outpatient queue congestion.</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
};

export const Dashboard: React.FC = () => {
  return (
    <ErrorBoundaryWrapper name="DashboardFeature">
      <DashboardContent />
    </ErrorBoundaryWrapper>
  );
};
export default Dashboard;
