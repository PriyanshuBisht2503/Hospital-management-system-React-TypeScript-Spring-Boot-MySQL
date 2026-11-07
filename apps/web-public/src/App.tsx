import React from 'react';
import { Button, Card, Badge, ApiStatusBadge } from '@hms/ui';
import {
  Calendar,
  Search,
  PhoneCall,
  Activity,
  Heart,
  Brain,
  ShieldCheck,
  Stethoscope,
  Building2,
  Clock
} from 'lucide-react';

export default function App() {
  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* Top Bar with Emergency & Status */}
      <div style={{ backgroundColor: '#0f172a', color: '#94a3b8', fontSize: '13px', padding: '8px 24px' }}>
        <div style={{ maxWidth: '1200px', margin: '0 auto', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', color: '#ef4444', fontWeight: 600 }}>
              <PhoneCall size={14} /> 24/7 Emergency: 999
            </span>
            <span>|</span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
              <Clock size={14} /> CareHMS Hospital - Central Branch & North Clinic
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span style={{ fontSize: '12px' }}>Core HMS Backend:</span>
            <ApiStatusBadge />
          </div>
        </div>
      </div>

      {/* Main Navbar */}
      <header className="glass-nav" style={{ position: 'sticky', top: 0, zIndex: 100 }}>
        <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '16px 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{ width: '40px', height: '40px', borderRadius: '10px', backgroundColor: '#0d9488', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', boxShadow: '0 4px 10px rgba(13, 148, 136, 0.3)' }}>
              <Activity size={24} />
            </div>
            <div>
              <h1 style={{ margin: 0, fontSize: '20px', fontWeight: 800, letterSpacing: '-0.5px', color: '#0f172a' }}>
                CARE<span style={{ color: '#0d9488' }}>HMS</span>
              </h1>
              <span style={{ fontSize: '11px', color: '#64748b', fontWeight: 600, letterSpacing: '1px' }}>UAE ECOSYSTEM • L1 PUBLIC</span>
            </div>
          </div>

          <nav style={{ display: 'flex', alignItems: 'center', gap: '24px', fontSize: '14px', fontWeight: 600, color: '#334155' }}>
            <a href="#specialties" style={{ color: '#334155', textDecoration: 'none' }}>Specialties</a>
            <a href="#doctors" style={{ color: '#334155', textDecoration: 'none' }}>Find Doctors</a>
            <a href="#locations" style={{ color: '#334155', textDecoration: 'none' }}>Hospitals & Clinics</a>
            <a href="http://localhost:3001" style={{ color: '#0d9488', textDecoration: 'none', display: 'flex', alignItems: 'center', gap: '4px' }}>
              Patient Portal (L2) →
            </a>
            <a href="http://localhost:3002" style={{ color: '#0369a1', textDecoration: 'none', display: 'flex', alignItems: 'center', gap: '4px' }}>
              Staff HMS (L4) →
            </a>
          </nav>
        </div>
      </header>

      {/* Hero Section */}
      <section className="hero-gradient" style={{ color: '#ffffff', padding: '72px 24px 96px', position: 'relative' }}>
        <div style={{ maxWidth: '1200px', margin: '0 auto', display: 'grid', gridTemplateColumns: '1.2fr 0.8fr', gap: '48px', alignItems: 'center' }}>
          <div>
            <Badge variant="info" style={{ backgroundColor: 'rgba(255, 255, 255, 0.15)', color: '#ccfbf1', border: '1px solid rgba(255, 255, 255, 0.25)', marginBottom: '16px' }}>
              ★ JCI Accredited Hospital Network • UAE
            </Badge>
            <h2 style={{ fontSize: '48px', fontWeight: 800, lineHeight: 1.15, margin: '0 0 20px 0', letterSpacing: '-1px' }}>
              We'll Treat You Well. World-Class Healthcare in Dubai.
            </h2>
            <p style={{ fontSize: '18px', color: '#cbd5e1', lineHeight: 1.6, margin: '0 0 32px 0', maxWidth: '540px' }}>
              Consult UAE's top-tier medical specialists, book appointments effortlessly, and experience integrated digital care with CareHMS.
            </p>
            <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
              <Button size="lg" style={{ backgroundColor: '#ffffff', color: '#0d9488' }} onClick={() => window.location.href = 'http://localhost:3001'}>
                <Calendar size={18} /> Book Appointment
              </Button>
              <Button size="lg" variant="outline" style={{ color: '#ffffff', borderColor: 'rgba(255,255,255,0.4)' }}>
                <Search size={18} /> Find a Doctor
              </Button>
            </div>
          </div>

          {/* Quick Doctor Search Card */}
          <Card padding="lg" style={{ backgroundColor: '#ffffff', color: '#0f172a', boxShadow: '0 20px 35px rgba(0, 0, 0, 0.2)' }}>
            <h3 style={{ margin: '0 0 16px 0', fontSize: '20px', fontWeight: 700 }}>Find a Specialist</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div>
                <label style={{ fontSize: '13px', fontWeight: 600, color: '#475569', marginBottom: '6px', display: 'block' }}>Department / Specialty</label>
                <select style={{ width: '100%', padding: '10px 12px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '14px' }}>
                  <option>Cardiology & Heart Care</option>
                  <option>Orthopaedics & Joint Surgery</option>
                  <option>Neurology & Brain Sciences</option>
                  <option>Paediatrics & Neonatology</option>
                  <option>Obstetrics & Gynaecology</option>
                </select>
              </div>
              <div>
                <label style={{ fontSize: '13px', fontWeight: 600, color: '#475569', marginBottom: '6px', display: 'block' }}>Hospital Branch</label>
                <select style={{ width: '100%', padding: '10px 12px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '14px' }}>
                  <option>CareHMS Central Branch (Dubai)</option>
                  <option>CareHMS North Clinic (Dubai)</option>
                </select>
              </div>
              <Button size="md" style={{ marginTop: '8px' }}>
                <Search size={16} /> Search 250+ Doctors
              </Button>
            </div>
          </Card>
        </div>
      </section>

      {/* Centers of Excellence */}
      <section id="specialties" style={{ padding: '64px 24px', maxWidth: '1200px', margin: '0 auto', width: '100%' }}>
        <div style={{ textAlign: 'center', marginBottom: '48px' }}>
          <Badge variant="neutral">Centres of Excellence</Badge>
          <h2 style={{ fontSize: '32px', fontWeight: 800, margin: '12px 0 8px 0', color: '#0f172a' }}>
            Comprehensive Multi-Specialty Care
          </h2>
          <p style={{ color: '#64748b', fontSize: '16px' }}>
            Equipped with state-of-the-art medical technology and internationally accredited clinicians.
          </p>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '24px' }}>
          {[
            { title: 'Cardiac Sciences', desc: 'Comprehensive non-invasive & interventional cardiology.', icon: Heart, count: '14 Consultants' },
            { title: 'Neuro Sciences', desc: 'Advanced stroke care, neurology & micro-neurosurgery.', icon: Brain, count: '9 Specialists' },
            { title: 'Orthopaedics', desc: 'Robotic joint replacement and sports medicine center.', icon: Activity, count: '18 Surgeons' },
            { title: 'Internal Medicine', desc: 'Holistic chronic illness management and preventive care.', icon: Stethoscope, count: '22 Doctors' },
          ].map((item, i) => {
            const Icon = item.icon;
            return (
              <Card key={i} padding="lg" style={{ transition: 'transform 0.2s ease, box-shadow 0.2s ease', cursor: 'pointer' }}>
                <div style={{ width: '48px', height: '48px', borderRadius: '12px', backgroundColor: '#f0fdfa', color: '#0d9488', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '16px' }}>
                  <Icon size={24} />
                </div>
                <h3 style={{ margin: '0 0 8px 0', fontSize: '18px', fontWeight: 700, color: '#0f172a' }}>{item.title}</h3>
                <p style={{ margin: '0 0 16px 0', fontSize: '14px', color: '#64748b', lineHeight: 1.5 }}>{item.desc}</p>
                <span style={{ fontSize: '12px', fontWeight: 600, color: '#0d9488' }}>{item.count} →</span>
              </Card>
            );
          })}
        </div>
      </section>

      {/* Footer */}
      <footer style={{ marginTop: 'auto', backgroundColor: '#0f172a', color: '#94a3b8', padding: '48px 24px 24px', borderTop: '1px solid #1e293b' }}>
        <div style={{ maxWidth: '1200px', margin: '0 auto', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '24px' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#ffffff', fontWeight: 700, marginBottom: '8px' }}>
              <ShieldCheck size={20} color="#0d9488" /> CareHMS Hospitals UAE
            </div>
            <p style={{ margin: 0, fontSize: '13px' }}>© 2026 CareHMS Healthcare. All rights reserved. Monorepo Layer 1 Public App.</p>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <span style={{ fontSize: '13px' }}>API Health Status:</span>
            <ApiStatusBadge />
          </div>
        </div>
      </footer>
    </div>
  );
}
