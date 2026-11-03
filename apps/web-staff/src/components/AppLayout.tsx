import React from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getPhase2Nav } from '../lib/navigation';
import { SidebarLayout, NavItem } from '@hms/ui';
import {
  LayoutDashboard,
  Users,
  Building2,
  GitFork,
  Stethoscope,
  DoorClosed,
  UserCheck,
  FileText,
  Calendar,
  Clock,
} from 'lucide-react';

export const AppLayout: React.FC = () => {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  if (!user) return null;

  const currentPath = location.pathname;

  // All Phase 2 pages are live now.
  const phase2Nav: NavItem[] = getPhase2Nav(user.role, user.doctorId)
    .filter((item) => item.href === '/patients' || item.href === '/appointments' || item.href === '/schedules' || item.href.startsWith('/schedules/') || item.href === '/my-day' || item.href === '/day-agenda')
    .map((item) => {
      const isSchedule = item.href === '/schedules' || item.href.startsWith('/schedules/');
      const isAppointments = item.href === '/appointments';
      const isMyDay = item.href === '/my-day';
      const isAgenda = item.href === '/day-agenda';
      return {
        label: item.label,
        href: item.href,
        icon: isMyDay ? <Clock size={18} /> : isSchedule || isAppointments || isAgenda ? <Calendar size={18} /> : <Users size={18} />,
        active: isSchedule ? currentPath.startsWith('/schedules') : isAppointments ? currentPath.startsWith('/appointments') : isAgenda ? currentPath.startsWith('/day-agenda') : isMyDay ? currentPath.startsWith('/my-day') : currentPath.startsWith('/patients'),
      };
    });

  let navItems: NavItem[] = [];

  if (user.role === 'ADMIN') {
    navItems = [
      {
        label: 'Dashboard',
        href: '/dashboard',
        icon: <LayoutDashboard size={18} />,
        active: currentPath === '/dashboard' || currentPath === '/',
      },
      ...phase2Nav,
      {
        label: 'Users',
        href: '/users',
        icon: <Users size={18} />,
        active: currentPath.startsWith('/users'),
      },
      {
        label: 'Branches',
        href: '/branches',
        icon: <Building2 size={18} />,
        active: currentPath.startsWith('/branches'),
      },
      {
        label: 'Departments',
        href: '/departments',
        icon: <GitFork size={18} />,
        active: currentPath.startsWith('/departments'),
      },
      {
        label: 'Specialties',
        href: '/specialties',
        icon: <Stethoscope size={18} />,
        active: currentPath.startsWith('/specialties'),
      },
      {
        label: 'Rooms',
        href: '/rooms',
        icon: <DoorClosed size={18} />,
        active: currentPath.startsWith('/rooms'),
      },
      {
        label: 'Doctors',
        href: '/doctors',
        icon: <UserCheck size={18} />,
        active: currentPath.startsWith('/doctors'),
      },
      {
        label: 'Audit Log',
        href: '/audit-logs',
        icon: <FileText size={18} />,
        active: currentPath.startsWith('/audit-logs'),
      },
    ];
  } else {
    // DOCTOR and RECEPTIONIST have only Dashboard
    navItems = [
      {
        label: 'Dashboard',
        href: '/dashboard',
        icon: <LayoutDashboard size={18} />,
        active: currentPath === '/dashboard' || currentPath === '/',
      },
      ...phase2Nav,
    ];
  }

  return (
    <SidebarLayout
      navItems={navItems}
      user={{
        fullName: user.fullName,
        role: user.role,
        email: user.email,
      }}
      onNavigate={(href) => navigate(href)}
      onLogout={() => logout()}
    >
      <Outlet />
    </SidebarLayout>
  );
};

