import React from 'react';
import { Link, NavLink } from 'react-router-dom';
import { buildPatientAppUrl } from '../lib/patientAppUrl';

const NAV_ITEMS = [
  { to: '/', label: 'Home', end: true },
  { to: '/specialties', label: 'Specialties' },
  { to: '/doctors', label: 'Doctors' },
  { to: '/branches', label: 'Branches' },
  { to: '/about', label: 'About' },
];

export const Layout: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div>
    <a className="skip-link" href="#main">Skip to main content</a>

    <header className="site-header">
      <div className="container" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '16px', paddingTop: '14px', paddingBottom: '14px', flexWrap: 'wrap' }}>
        <Link to="/" style={{ display: 'flex', alignItems: 'center', gap: '10px', textDecoration: 'none' }} aria-label="CareHMS home">
          <span style={{ width: '34px', height: '34px', borderRadius: '9px', backgroundColor: '#0d9488', color: '#fff', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800 }}>C</span>
          <span style={{ fontSize: '18px', fontWeight: 800, color: '#0f172a' }}>Care<span style={{ color: '#0d9488' }}>HMS</span></span>
        </Link>

        <nav aria-label="Main navigation">
          <ul className="nav-list">
            {NAV_ITEMS.map((item) => (
              <li key={item.to}>
                <NavLink to={item.to} end={item.end} className={({ isActive }) => (isActive ? 'active' : undefined)}>
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <a
          href={buildPatientAppUrl('/login')}
          style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', backgroundColor: '#0d9488', color: '#fff', padding: '8px 14px', borderRadius: '8px', textDecoration: 'none', fontWeight: 600, fontSize: '14px' }}
        >
          Patient login
        </a>
      </div>
    </header>

    <main id="main" className="container main-content">{children}</main>

    <footer style={{ backgroundColor: '#0f172a', color: '#94a3b8', padding: '32px 0' }}>
      <div className="container" style={{ display: 'flex', justifyContent: 'space-between', gap: '16px', flexWrap: 'wrap', fontSize: '13px' }}>
        <p style={{ margin: 0 }}>CareHMS — a fictional hospital demo project. Not a real healthcare provider.</p>
        <nav aria-label="Footer navigation">
          <ul className="nav-list">
            <li><Link to="/specialties" style={{ color: '#cbd5e1' }}>Specialties</Link></li>
            <li><Link to="/doctors" style={{ color: '#cbd5e1' }}>Doctors</Link></li>
            <li><Link to="/about" style={{ color: '#cbd5e1' }}>About</Link></li>
            <li><a href={buildPatientAppUrl('/login')} style={{ color: '#cbd5e1' }}>Patient login</a></li>
          </ul>
        </nav>
      </div>
    </footer>
  </div>
);
