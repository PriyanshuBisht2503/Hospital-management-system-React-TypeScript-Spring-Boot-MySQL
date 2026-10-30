import React, { useState } from 'react';
import {
  Button,
  Card,
  Badge,
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
  ApiStatusBadge
} from '@hms/ui';
import {
  Activity,
  Users,
  Calendar,
  Bed,
  DollarSign,
  Shield,
  Stethoscope,
  ClipboardList,
  Search,
  CheckCircle2,
  AlertCircle
} from 'lucide-react';

type StaffRole = 'ADMIN' | 'DOCTOR' | 'RECEPTIONIST';

export default function App() {
  const [currentRole, setCurrentRole] = useState<StaffRole>('DOCTOR');

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', backgroundColor: '#f1f5f9' }}>
      {/* Top Staff Navigation */}
      <header style={{ backgroundColor: '#0f172a', color: '#ffffff', padding: '12px 24px', position: 'sticky', top: 0, zIndex: 100 }}>
        <div style={{ maxWidth: '1350px', margin: '0 auto', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
            <div style={{ width: '38px', height: '38px', borderRadius: '8px', backgroundColor: '#0d9488', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#ffffff' }}>
              <Activity size={22} />
            </div>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontSize: '18px', fontWeight: 800, letterSpacing: '-0.3px' }}>ASTER HMS</span>
                <Badge variant="neutral" style={{ backgroundColor: '#1e293b', color: '#94a3b8', border: '1px solid #334155' }}>
                  L4 CORE HMS
                </Badge>
              </div>
              <span style={{ fontSize: '11px', color: '#94a3b8' }}>Aster Hospital - Mankhool Branch</span>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
            {/* Role Switcher */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', backgroundColor: '#1e293b', padding: '4px', borderRadius: '8px' }}>
              {(['ADMIN', 'DOCTOR', 'RECEPTIONIST'] as StaffRole[]).map((role) => (
                <button
                  key={role}
                  onClick={() => setCurrentRole(role)}
                  style={{
                    backgroundColor: currentRole === role ? '#0d9488' : 'transparent',
                    color: currentRole === role ? '#ffffff' : '#94a3b8',
                    border: 'none',
                    borderRadius: '6px',
                    padding: '6px 12px',
                    fontSize: '12px',
                    fontWeight: 700,
                    cursor: 'pointer',
                    transition: 'all 0.15s ease',
                  }}
                >
                  {role}
                </button>
              ))}
            </div>

            <div style={{ height: '24px', width: '1px', backgroundColor: '#334155' }} />

            {/* Core API Health */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontSize: '12px', color: '#94a3b8' }}>Core API:</span>
              <ApiStatusBadge />
            </div>

            <div style={{ height: '24px', width: '1px', backgroundColor: '#334155' }} />

            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{ width: '34px', height: '34px', borderRadius: '50%', backgroundColor: '#0d9488', color: '#ffffff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: '12px' }}>
                {currentRole[0]}
              </div>
              <div>
                <div style={{ fontSize: '13px', fontWeight: 700 }}>Staff User</div>
                <div style={{ fontSize: '11px', color: '#94a3b8' }}>Role: {currentRole}</div>
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* Main Staff Dashboard */}
      <main style={{ maxWidth: '1350px', margin: '0 auto', padding: '28px 24px', width: '100%', flex: 1 }}>
        {/* KPI Strip */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '18px', marginBottom: '28px' }}>
          {[
            { label: 'OPD Patients Waiting', val: '18 Patients', sub: 'Avg wait: 12 mins', icon: Users, color: '#0d9488', bg: '#f0fdfa' },
            { label: 'Active Consultations', val: '8 In-Progress', sub: 'Across 12 clinics', icon: Stethoscope, color: '#0284c7', bg: '#f0f9ff' },
            { label: 'Bed Occupancy (IPD)', val: '86% (142 / 165)', sub: '23 Beds available', icon: Bed, color: '#8b5cf6', bg: '#f5f3ff' },
            { label: "Today's Collections", val: 'AED 84,250', sub: 'Cash, Card & Insurance', icon: DollarSign, color: '#10b981', bg: '#ecfdf5' },
          ].map((kpi, idx) => {
            const Icon = kpi.icon;
            return (
              <Card key={idx} padding="md">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                  <span style={{ fontSize: '13px', fontWeight: 600, color: '#64748b' }}>{kpi.label}</span>
                  <div style={{ width: '36px', height: '36px', borderRadius: '8px', backgroundColor: kpi.bg, color: kpi.color, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Icon size={18} />
                  </div>
                </div>
                <div style={{ fontSize: '22px', fontWeight: 800, color: '#0f172a', marginBottom: '4px' }}>{kpi.val}</div>
                <div style={{ fontSize: '12px', color: '#94a3b8' }}>{kpi.sub}</div>
              </Card>
            );
          })}
        </div>

        {/* Clinical OPD Queue */}
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '24px' }}>
          <Card padding="lg">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <div>
                <h2 style={{ fontSize: '18px', fontWeight: 700, margin: '0 0 4px 0', color: '#0f172a' }}>
                  Today's OPD Queue & Triage Status
                </h2>
                <span style={{ fontSize: '13px', color: '#64748b' }}>Live patient workflow • Synced with Core HMS API</span>
              </div>
              <Button size="sm">
                + Register Walk-In
              </Button>
            </div>

            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Token</TableHead>
                  <TableHead>Patient Details</TableHead>
                  <TableHead>Doctor / Clinic</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[
                  { token: 'T-101', name: 'Khalid Al-Hashemi', mrn: 'AST-2026-1049', doctor: 'Dr. Ayman Saeed (Cardio)', status: 'In-Consultation', variant: 'success' as const },
                  { token: 'T-102', name: 'Sara Jenkins', mrn: 'AST-2026-1052', doctor: 'Dr. Priya Sharma (Pediatrics)', status: 'Checked-In', variant: 'info' as const },
                  { token: 'T-103', name: 'Mohammed Iqbal', mrn: 'AST-2026-1088', doctor: 'Dr. Ayman Saeed (Cardio)', status: 'Waiting', variant: 'warning' as const },
                  { token: 'T-104', name: 'Elena Rostova', mrn: 'AST-2026-1102', doctor: 'Dr. Chen Wei (Orthopaedics)', status: 'Booked', variant: 'neutral' as const },
                ].map((row, i) => (
                  <TableRow key={i}>
                    <TableCell style={{ fontWeight: 700, color: '#0d9488' }}>{row.token}</TableCell>
                    <TableCell>
                      <div style={{ fontWeight: 600, color: '#0f172a' }}>{row.name}</div>
                      <div style={{ fontSize: '11px', color: '#64748b' }}>MRN: {row.mrn}</div>
                    </TableCell>
                    <TableCell style={{ fontSize: '13px' }}>{row.doctor}</TableCell>
                    <TableCell>
                      <Badge variant={row.variant}>{row.status}</Badge>
                    </TableCell>
                    <TableCell>
                      <Button size="sm" variant="outline">
                        Open EMR
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>

          {/* Core System Status */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <Card padding="lg">
              <h3 style={{ fontSize: '16px', fontWeight: 700, margin: '0 0 16px 0', color: '#0f172a' }}>
                Architecture & Security Verification
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 12px', backgroundColor: '#f8fafc', borderRadius: '8px' }}>
                  <span style={{ color: '#64748b' }}>Core Backend API</span>
                  <ApiStatusBadge />
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 12px', backgroundColor: '#f8fafc', borderRadius: '8px' }}>
                  <span style={{ color: '#64748b' }}>Active RBAC Mode</span>
                  <span style={{ fontWeight: 700, color: '#0f172a' }}>@PreAuthorize</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 12px', backgroundColor: '#f8fafc', borderRadius: '8px' }}>
                  <span style={{ color: '#64748b' }}>Hibernate ddl-auto</span>
                  <Badge variant="neutral">none (Flyway)</Badge>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 12px', backgroundColor: '#f8fafc', borderRadius: '8px' }}>
                  <span style={{ color: '#64748b' }}>Audit Log Middleware</span>
                  <span style={{ color: '#0d9488', fontWeight: 600 }}>Enforced in Phase 1</span>
                </div>
              </div>
            </Card>

            <Card padding="md" style={{ backgroundColor: '#0f172a', color: '#ffffff' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                <Shield size={16} color="#0d9488" />
                <span style={{ fontSize: '13px', fontWeight: 700 }}>4 Fixed Roles Policy</span>
              </div>
              <p style={{ margin: 0, fontSize: '12px', color: '#94a3b8', lineHeight: 1.5 }}>
                System roles are strictly limited to ADMIN, DOCTOR, RECEPTIONIST, and PATIENT. No extra roles may be introduced.
              </p>
            </Card>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer style={{ backgroundColor: '#ffffff', borderTop: '1px solid #e2e8f0', padding: '16px 24px', fontSize: '12px', color: '#64748b' }}>
        <div style={{ maxWidth: '1350px', margin: '0 auto', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>Aster HMS Core Internal Suite • Licensed for Hospital Operations.</div>
          <div style={{ display: 'flex', gap: '16px' }}>
            <a href="http://localhost:3000" style={{ color: '#0d9488', textDecoration: 'none' }}>Public Portal (3000)</a>
            <a href="http://localhost:3001" style={{ color: '#0284c7', textDecoration: 'none' }}>Patient App (3001)</a>
          </div>
        </div>
      </footer>
    </div>
  );
}
