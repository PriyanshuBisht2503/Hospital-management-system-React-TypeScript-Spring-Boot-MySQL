import React, { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { 
  LayoutDashboard, 
  Users, 
  CalendarRange, 
  CreditCard, 
  LogOut, 
  Activity,
  Menu,
  X
} from 'lucide-react';
import styles from './Sidebar.module.css';

export const Sidebar: React.FC = () => {
  const { user, logout } = useAuth();
  const [isOpen, setIsOpen] = useState(false);

  const hasRole = (role: string) => {
    return user?.roles.includes(role);
  };

  const showPatients = hasRole('ROLE_ADMIN') || hasRole('ROLE_RECEPTIONIST') || hasRole('ROLE_DOCTOR');

  const handleLinkClick = () => {
    // Auto collapse sidebar on link click (for mobile)
    setIsOpen(false);
  };

  return (
    <>
      <button 
        className={styles.menuToggle} 
        onClick={() => setIsOpen(!isOpen)} 
        aria-label="Toggle navigation drawer"
        aria-expanded={isOpen}
      >
        {isOpen ? <X size={20} /> : <Menu size={20} />}
      </button>

      <aside 
        className={`${styles.sidebar} ${isOpen ? styles.sidebarOpen : ''}`}
        aria-label="Sidebar navigation menu"
      >
        <div className={styles.brandContainer}>
          <Activity size={28} color="rgb(9, 133, 107)" />
          <h2 className={styles.brandText}>CareHMS</h2>
        </div>

        <nav className={styles.nav} aria-label="Main links options">
          <NavLink 
            to="/dashboard" 
            className={({ isActive }) => `${styles.link} ${isActive ? styles.linkActive : ''}`}
            onClick={handleLinkClick}
          >
            <LayoutDashboard size={20} />
            <span>Dashboard</span>
          </NavLink>

          {showPatients && (
            <NavLink 
              to="/patients" 
              className={({ isActive }) => `${styles.link} ${isActive ? styles.linkActive : ''}`}
              onClick={handleLinkClick}
            >
              <Users size={20} />
              <span>Patients</span>
            </NavLink>
          )}

          <NavLink 
            to="/appointments" 
            className={({ isActive }) => `${styles.link} ${isActive ? styles.linkActive : ''}`}
            onClick={handleLinkClick}
          >
            <CalendarRange size={20} />
            <span>Appointments</span>
          </NavLink>

          <NavLink 
            to="/billing" 
            className={({ isActive }) => `${styles.link} ${isActive ? styles.linkActive : ''}`}
            onClick={handleLinkClick}
          >
            <CreditCard size={20} />
            <span>Billing</span>
          </NavLink>
        </nav>

        <div className={styles.footer}>
          <div className={styles.userInfo}>
            <p className={styles.userName}>{user?.firstName} {user?.lastName}</p>
            <p className={styles.userRole}>{user?.roles[0]?.replace('ROLE_', '')}</p>
          </div>
          <button onClick={logout} className={styles.logoutBtn} aria-label="Sign out of portal session">
            <LogOut size={18} />
            <span>Logout</span>
          </button>
        </div>
      </aside>
    </>
  );
};
export default Sidebar;
