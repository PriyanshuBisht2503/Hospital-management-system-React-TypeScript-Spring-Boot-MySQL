import React from 'react';
import { Button, Card, Badge, ApiStatusBadge } from '@hms/ui';
import {
  Calendar,
  FileText,
  Clock,
  User,
  HeartPulse,
  Pill,
  Video,
  ChevronRight,
  Shield,
  Bell
} from 'lucide-react';

export default function App() {
  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', backgroundColor: '#f8fafc' }}>
      {/* Top Patient Header */}
      <header style={{ backgroundColor: '#ffffff', borderBottom: '1px solid #e2e8f0', position: 'sticky', top: 0, zIndex: 100 }}>
        <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '14px 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{ width: '38px', height: '38px', borderRadius: '10px', backgroundColor: '#0284c7', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#ffffff' }}>
              <HeartPulse size={22} />
            </div>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontSize: '18px', fontWeight: 800, color: '#0f172a' }}>CareHMS</span>
                <Badge variant="info">Patient Portal (L2 / L3)</Badge>
              </div>
              <span style={{ fontSize: '11px', color: '#64748b' }}>Connected to CareHMS Core Engine</span>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontSize: '12px', color: '#64748b', fontWeight: 600 }}>API Status:</span>
              <ApiStatusBadge />
            </div>

            <div style={{ height: '24px', width: '1px', backgroundColor: '#e2e8f0' }} />

            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{ width: '34px', height: '34px', borderRadius: '50%', backgroundColor: '#e0f2fe', color: '#0369a1', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: '13px' }}>
                FA
              </div>
              <div>
                <div style={{ fontSize: '13px', fontWeight: 700, color: '#0f172a' }}>Fatima Al-Mansoor</div>
                <div style={{ fontSize: '11px', color: '#64748b' }}>MRN: AST-2026-9041</div>
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main style={{ maxWidth: '1200px', margin: '0 auto', padding: '32px 24px', width: '100%', flex: 1 }}>
        {/* Welcome Banner */}
        <div style={{ background: 'linear-gradient(135deg, #0284c7 0%, #0369a1 100%)', borderRadius: '16px', padding: '28px 32px', color: '#ffffff', marginBottom: '32px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <Badge variant="neutral" style={{ backgroundColor: 'rgba(255,255,255,0.2)', color: '#ffffff', border: 'none', marginBottom: '8px' }}>
              Role: PATIENT
            </Badge>
            <h1 style={{ fontSize: '26px', fontWeight: 800, margin: '0 0 8px 0' }}>Welcome back, Fatima</h1>
            <p style={{ margin: 0, color: '#e0f2fe', fontSize: '14px' }}>
              Your upcoming consultation is scheduled with Dr. Ayman Saeed (Cardiology) on Monday, 10:30 AM.
            </p>
          </div>
          <div style={{ display: 'flex', gap: '12px' }}>
            <Button style={{ backgroundColor: '#ffffff', color: '#0284c7' }}>
              <Calendar size={16} /> Book New Appointment
            </Button>
            <Button variant="outline" style={{ color: '#ffffff', borderColor: 'rgba(255,255,255,0.4)' }}>
              <Video size={16} /> Join Teleconsult
            </Button>
          </div>
        </div>

        {/* Quick Action Cards */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '20px', marginBottom: '36px' }}>
          {[
            { label: 'Upcoming Visits', count: '1 Active', icon: Calendar, color: '#0284c7', bg: '#f0f9ff' },
            { label: 'Lab & Diagnostic Reports', count: '4 Ready', icon: FileText, color: '#0d9488', bg: '#f0fdfa' },
            { label: 'E-Prescriptions', count: '2 Refills', icon: Pill, color: '#8b5cf6', bg: '#f5f3ff' },
            { label: 'Insurance & Claims', count: 'Daman Gold (Active)', icon: Shield, color: '#f59e0b', bg: '#fffbeb' },
          ].map((card, idx) => {
            const Icon = card.icon;
            return (
              <Card key={idx} padding="md" style={{ cursor: 'pointer', transition: 'transform 0.15s ease' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px' }}>
                  <div style={{ width: '40px', height: '40px', borderRadius: '10px', backgroundColor: card.bg, color: card.color, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Icon size={20} />
                  </div>
                  <span style={{ fontSize: '13px', fontWeight: 600, color: '#64748b' }}>{card.label}</span>
                </div>
                <div style={{ fontSize: '17px', fontWeight: 700, color: '#0f172a' }}>{card.count}</div>
              </Card>
            );
          })}
        </div>

        {/* Upcoming Consultations & Records */}
        <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr', gap: '28px' }}>
          <Card padding="lg">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <h2 style={{ fontSize: '18px', fontWeight: 700, margin: 0, color: '#0f172a' }}>Upcoming Consultations</h2>
              <span style={{ fontSize: '13px', color: '#0284c7', fontWeight: 600, cursor: 'pointer' }}>View all history →</span>
            </div>

            <div style={{ border: '1px solid #e2e8f0', borderRadius: '12px', padding: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
                <div style={{ width: '48px', height: '48px', borderRadius: '12px', backgroundColor: '#f0fdfa', color: '#0d9488', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800 }}>
                  DR
                </div>
                <div>
                  <h3 style={{ margin: '0 0 4px 0', fontSize: '15px', fontWeight: 700, color: '#0f172a' }}>Dr. Ayman Saeed</h3>
                  <div style={{ fontSize: '13px', color: '#64748b' }}>Consultant Interventional Cardiologist • CareHMS Central Branch</div>
                  <div style={{ display: 'flex', gap: '12px', marginTop: '6px', fontSize: '12px', color: '#334155' }}>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                      <Calendar size={13} color="#0284c7" /> Mon, 03 Nov 2026
                    </span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                      <Clock size={13} color="#0284c7" /> 10:30 AM (Slot 12)
                    </span>
                  </div>
                </div>
              </div>
              <Badge variant="success">Confirmed</Badge>
            </div>
          </Card>

          {/* Connected Services */}
          <Card padding="lg">
            <h2 style={{ fontSize: '18px', fontWeight: 700, margin: '0 0 16px 0', color: '#0f172a' }}>System Verification</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 12px', backgroundColor: '#f8fafc', borderRadius: '8px' }}>
                <span style={{ color: '#64748b' }}>Client App</span>
                <span style={{ fontWeight: 600, color: '#0f172a' }}>apps/web-patient (Port 3001)</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 12px', backgroundColor: '#f8fafc', borderRadius: '8px' }}>
                <span style={{ color: '#64748b' }}>Shared UI</span>
                <span style={{ fontWeight: 600, color: '#0f172a' }}>packages/ui (Connected)</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 12px', backgroundColor: '#f8fafc', borderRadius: '8px' }}>
                <span style={{ color: '#64748b' }}>Core API Health</span>
                <ApiStatusBadge />
              </div>
            </div>
          </Card>
        </div>
      </main>

      {/* Footer */}
      <footer style={{ backgroundColor: '#ffffff', borderTop: '1px solid #e2e8f0', padding: '20px 24px', fontSize: '13px', color: '#64748b' }}>
        <div style={{ maxWidth: '1200px', margin: '0 auto', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>© 2026 CareHMS Patient Portal. Powered by CareHMS Core API.</div>
          <div style={{ display: 'flex', gap: '16px' }}>
            <a href="http://localhost:3000" style={{ color: '#0284c7', textDecoration: 'none' }}>Public Site (L1)</a>
            <a href="http://localhost:3002" style={{ color: '#0284c7', textDecoration: 'none' }}>Staff HMS (L4)</a>
          </div>
        </div>
      </footer>
    </div>
  );
}
