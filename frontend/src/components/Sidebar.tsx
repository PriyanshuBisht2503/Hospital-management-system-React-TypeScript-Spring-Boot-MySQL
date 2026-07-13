import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { 
  LayoutDashboard, 
  Users, 
  CalendarRange, 
  CreditCard, 
  LogOut, 
  Activity 
} from 'lucide-react';

export const Sidebar: React.FC = () => {
  const { user, logout } = useAuth();

  const hasRole = (role: string) => {
    return user?.roles.includes(role);
  };

  const showPatients = hasRole('ROLE_ADMIN') || hasRole('ROLE_RECEPTIONIST') || hasRole('ROLE_DOCTOR');

  return (
    <aside style={styles.sidebar}>
      <div style={styles.brandContainer}>
        <Activity size={28} color="rgb(9, 133, 107)" />
        <h2 style={styles.brandText}>CareHMS</h2>
      </div>

      <nav style={styles.nav}>
        <NavLink 
          to="/dashboard" 
          style={({ isActive }) => ({ ...styles.link, ...(isActive ? styles.linkActive : {}) })}
        >
          <LayoutDashboard size={20} />
          <span>Dashboard</span>
        </NavLink>

        {showPatients && (
          <NavLink 
            to="/patients" 
            style={({ isActive }) => ({ ...styles.link, ...(isActive ? styles.linkActive : {}) })}
          >
            <Users size={20} />
            <span>Patients</span>
          </NavLink>
        )}

        <NavLink 
          to="/appointments" 
          style={({ isActive }) => ({ ...styles.link, ...(isActive ? styles.linkActive : {}) })}
        >
          <CalendarRange size={20} />
          <span>Appointments</span>
        </NavLink>

        <NavLink 
          to="/billing" 
          style={({ isActive }) => ({ ...styles.link, ...(isActive ? styles.linkActive : {}) })}
        >
          <CreditCard size={20} />
          <span>Billing</span>
        </NavLink>
      </nav>

      <div style={styles.footer}>
        <div style={styles.userInfo}>
          <p style={styles.userName}>{user?.firstName} {user?.lastName}</p>
          <p style={styles.userRole}>{user?.roles[0]?.replace('ROLE_', '')}</p>
        </div>
        <button onClick={logout} style={styles.logoutBtn}>
          <LogOut size={18} />
          <span>Logout</span>
        </button>
      </div>
    </aside>
  );
};

const styles: Record<string, React.CSSProperties> = {
  sidebar: {
    width: 'var(--sidebar-width)',
    backgroundColor: 'var(--bg-sidebar)',
    color: '#94a3b8',
    display: 'flex',
    flexDirection: 'column',
    minHeight: '100vh',
    padding: '1.5rem',
    borderRight: '1px solid #1e293b',
  },
  brandContainer: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
    marginBottom: '2.5rem',
  },
  brandText: {
    fontSize: '1.25rem',
    fontWeight: 800,
    color: '#ffffff',
  },
  nav: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
    flex: 1,
  },
  link: {
    display: 'flex',
    alignItems: 'center',
    gap: '1rem',
    padding: '0.85rem 1rem',
    borderRadius: 'var(--radius-sm)',
    fontSize: '0.95rem',
    fontWeight: 600,
    color: '#94a3b8',
    transition: 'all var(--transition-fast)',
  },
  linkActive: {
    backgroundColor: 'rgba(9, 133, 107, 0.15)',
    color: '#ffffff',
    borderLeft: '3px solid rgb(9, 133, 107)',
  },
  footer: {
    marginTop: 'auto',
    display: 'flex',
    flexDirection: 'column',
    gap: '1rem',
    paddingTop: '1.5rem',
    borderTop: '1px solid #1e293b',
  },
  userInfo: {
    fontSize: '0.875rem',
  },
  userName: {
    color: '#ffffff',
    fontWeight: 600,
  },
  userRole: {
    fontSize: '0.75rem',
    color: '#64748b',
  },
  logoutBtn: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '0.5rem',
    width: '100%',
    padding: '0.75rem',
    backgroundColor: '#1e293b',
    color: '#ffffff',
    border: 'none',
    borderRadius: 'var(--radius-sm)',
    fontWeight: 600,
    cursor: 'pointer',
    transition: 'background var(--transition-fast)',
  },
};
