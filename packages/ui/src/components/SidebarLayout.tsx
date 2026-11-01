import React from 'react';
import { Badge } from './Badge';
import { Button } from './Button';

export interface NavItem {
  label: string;
  href: string;
  icon?: React.ReactNode;
  active?: boolean;
}

export interface SidebarLayoutProps {
  navItems: NavItem[];
  user: {
    fullName: string;
    role: string;
    email: string;
  };
  onNavigate: (href: string) => void;
  onLogout: () => void;
  children: React.ReactNode;
}

export const SidebarLayout: React.FC<SidebarLayoutProps> = ({
  navItems,
  user,
  onNavigate,
  onLogout,
  children,
}) => {
  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: '#f8fafc', fontFamily: 'Inter, system-ui, sans-serif' }}>
      {/* Sidebar Navigation */}
      <aside
        style={{
          width: '260px',
          backgroundColor: '#0f172a',
          color: '#f8fafc',
          display: 'flex',
          flexDirection: 'column',
          flexShrink: 0,
          borderRight: '1px solid #1e293b',
        }}
      >
        {/* Brand */}
        <div style={{ padding: '24px 20px', borderBottom: '1px solid #1e293b' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div
              style={{
                width: '36px',
                height: '36px',
                borderRadius: '8px',
                backgroundColor: '#0d9488',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#ffffff',
                fontWeight: 700,
                fontSize: '18px',
              }}
            >
              A
            </div>
            <div>
              <div style={{ fontSize: '16px', fontWeight: 700, letterSpacing: '-0.3px', color: '#ffffff' }}>
                ASTER HMS
              </div>
              <div style={{ fontSize: '11px', color: '#94a3b8' }}>
                Hospital Staff Portal
              </div>
            </div>
          </div>
        </div>

        {/* Navigation Links */}
        <nav style={{ padding: '16px 12px', flex: 1, display: 'flex', flexDirection: 'column', gap: '4px' }}>
          {navItems.map((item) => (
            <button
              key={item.href}
              onClick={() => onNavigate(item.href)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                width: '100%',
                padding: '10px 14px',
                borderRadius: '8px',
                border: 'none',
                backgroundColor: item.active ? '#1e293b' : 'transparent',
                color: item.active ? '#2dd4bf' : '#94a3b8',
                fontWeight: item.active ? 600 : 500,
                fontSize: '14px',
                cursor: 'pointer',
                textAlign: 'left',
                transition: 'all 0.15s ease',
              }}
              onMouseEnter={(e) => {
                if (!item.active) {
                  e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.05)';
                  e.currentTarget.style.color = '#ffffff';
                }
              }}
              onMouseLeave={(e) => {
                if (!item.active) {
                  e.currentTarget.style.backgroundColor = 'transparent';
                  e.currentTarget.style.color = '#94a3b8';
                }
              }}
            >
              {item.icon && <span style={{ display: 'flex', alignItems: 'center' }}>{item.icon}</span>}
              <span>{item.label}</span>
            </button>
          ))}
        </nav>

        {/* Footer info */}
        <div style={{ padding: '16px 20px', borderTop: '1px solid #1e293b', fontSize: '11px', color: '#64748b' }}>
          Aster Hospital • UAE
        </div>
      </aside>

      {/* Main Layout Area */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        {/* Top Header */}
        <header
          style={{
            height: '64px',
            backgroundColor: '#ffffff',
            borderBottom: '1px solid #e2e8f0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            padding: '0 32px',
            gap: '16px',
            position: 'sticky',
            top: 0,
            zIndex: 10,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div
              style={{
                width: '34px',
                height: '34px',
                borderRadius: '50%',
                backgroundColor: '#ccfbf1',
                color: '#0f766e',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 600,
                fontSize: '13px',
              }}
            >
              {user.fullName.charAt(0).toUpperCase()}
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: '13px', fontWeight: 600, color: '#0f172a' }}>
                {user.fullName}
              </div>
              <div style={{ fontSize: '11px', color: '#64748b' }}>
                {user.email}
              </div>
            </div>
            <Badge variant="info" style={{ marginLeft: '4px' }}>
              {user.role}
            </Badge>
          </div>

          <Button
            variant="outline"
            size="sm"
            onClick={onLogout}
            style={{ marginLeft: '12px', padding: '6px 12px' }}
          >
            Sign Out
          </Button>
        </header>

        {/* Page Content */}
        <main style={{ flex: 1, padding: '32px', minWidth: 0, overflowY: 'auto' }}>
          {children}
        </main>
      </div>
    </div>
  );
};
