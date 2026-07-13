import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { CreditCard, Printer, ShieldAlert, CheckCircle } from 'lucide-react';

interface Invoice {
  id: number;
  patient: {
    id: number;
    user: { firstName: string; lastName: string };
  };
  totalAmount: number;
  taxAmount: number;
  netAmount: number;
  status: string;
  dueDate: string;
}

export const Billing: React.FC = () => {
  const { user } = useAuth();
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [showGenerateForm, setShowGenerateForm] = useState(false);

  // Invoicing fields
  const [appointmentId, setAppointmentId] = useState('');
  const [fee, setFee] = useState('500.00'); // Standard default consultation fee

  // Payment fields
  const [activePaymentInvoice, setActivePaymentInvoice] = useState<Invoice | null>(null);
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [transactionId, setTransactionId] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const isPatient = user?.roles.includes('ROLE_PATIENT');
  const canBilling = user?.roles.includes('ROLE_ADMIN') || user?.roles.includes('ROLE_RECEPTIONIST');

  const loadInvoices = async () => {
    try {
      if (isPatient) {
        // Patient role: query own invoices (patientId=1 default demo)
        const res = await api.get('/billing/invoices/patient/1');
        setInvoices(res.data.content || []);
      } else {
        // Staff: query general invoices. Defaulting to patientId=1
        const res = await api.get('/billing/invoices/patient/1');
        setInvoices(res.data.content || []);
      }
    } catch (err) {
      setError('Failed to fetch billing invoicing history');
    }
  };

  useEffect(() => {
    loadInvoices();
  }, []);

  const handleGenerateInvoice = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccess('');

    try {
      await api.post(`/billing/invoices/consultation?appointmentId=${appointmentId}&fee=${fee}`);
      setSuccess('Consultation invoice generated successfully!');
      setShowGenerateForm(false);
      loadInvoices();
      setAppointmentId('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Invoicing failed. Verify appointment ID is active and not duplicate.');
    } finally {
      setLoading(false);
    }
  };

  const handleRecordPayment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activePaymentInvoice) return;
    setLoading(true);
    setError('');
    setSuccess('');

    const payload = {
      invoiceId: activePaymentInvoice.id,
      amount: activePaymentInvoice.netAmount,
      paymentMethod,
      transactionId: transactionId || 'TXN-' + Date.now(),
    };

    try {
      await api.post('/billing/payments', payload);
      setSuccess('Payment successfully processed & reconciled via gateway!');
      setActivePaymentInvoice(null);
      setTransactionId('');
      loadInvoices();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Payment processing failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <div style={styles.headerText}>
          <h1 style={styles.title}>Invoicing & Payments</h1>
          <p style={styles.subtitle}>Track patient invoices, processing fees, and payment settlements.</p>
        </div>
        {canBilling && (
          <button className="btn btn-primary" onClick={() => setShowGenerateForm(!showGenerateForm)}>
            <CreditCard size={18} />
            <span>{showGenerateForm ? 'View Invoices' : 'Generate Invoice'}</span>
          </button>
        )}
      </header>

      {error && (
        <div className="badge-danger" style={styles.errorBox}>
          <ShieldAlert size={18} />
          <span>{error}</span>
        </div>
      )}

      {success && (
        <div className="badge-success" style={styles.errorBox}>
          <CheckCircle size={18} />
          <span>{success}</span>
        </div>
      )}

      {showGenerateForm ? (
        <div className="card" style={styles.formCard}>
          <h3 style={styles.formTitle}>Generate Consultation Invoice</h3>
          <form onSubmit={handleGenerateInvoice} style={styles.form}>
            <div className="form-group">
              <label className="form-label">Appointment ID</label>
              <input
                type="number"
                className="form-input"
                placeholder="Enter confirmed appointment reference ID..."
                value={appointmentId}
                onChange={e => setAppointmentId(e.target.value)}
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">Consultation Fee (INR)</label>
              <input
                type="number"
                className="form-input"
                value={fee}
                onChange={e => setFee(e.target.value)}
                required
              />
            </div>

            <button type="submit" className="btn btn-primary" style={{ marginTop: '1rem' }} disabled={loading}>
              {loading ? 'Processing tax equations...' : 'Generate Bill'}
            </button>
          </form>
        </div>
      ) : activePaymentInvoice ? (
        <div className="card" style={styles.formCard}>
          <h3 style={styles.formTitle}>Process Payment Gateway charge</h3>
          <p style={{ marginBottom: '1.5rem', color: 'var(--text-muted)' }}>
            Processing transaction for Invoice #{activePaymentInvoice.id} of Patient: {activePaymentInvoice.patient.user.firstName} {activePaymentInvoice.patient.user.lastName}.
            Net settlement amount: <strong>INR {activePaymentInvoice.netAmount}</strong> (includes 18% GST).
          </p>
          <form onSubmit={handleRecordPayment} style={styles.form}>
            <div className="form-group">
              <label className="form-label">Payment Method</label>
              <select className="form-input" value={paymentMethod} onChange={e => setPaymentMethod(e.target.value)}>
                <option value="CASH">Cash</option>
                <option value="CARD">Debit/Credit Card</option>
                <option value="UPI">UPI/Digital Transfer</option>
                <option value="INSURANCE">Insurance Settlement</option>
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Transaction reference ID (Optional)</label>
              <input
                type="text"
                className="form-input"
                placeholder="Auto-generated if blank..."
                value={transactionId}
                onChange={e => setTransactionId(e.target.value)}
              />
            </div>

            <div style={styles.row}>
              <button type="submit" className="btn btn-primary" style={{ flex: 1 }} disabled={loading}>
                {loading ? 'Processing charge gateway...' : 'Submit Payment'}
              </button>
              <button type="button" className="btn btn-outline" style={{ flex: 1 }} onClick={() => setActivePaymentInvoice(null)}>
                Cancel
              </button>
            </div>
          </form>
        </div>
      ) : (
        <div style={styles.listContainer}>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Invoice ID</th>
                  <th>Patient</th>
                  <th>Base Fee</th>
                  <th>GST (18%)</th>
                  <th>Net Bill</th>
                  <th>Due Date</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {invoices.length > 0 ? (
                  invoices.map(inv => (
                    <tr key={inv.id}>
                      <td>#{inv.id}</td>
                      <td style={{ fontWeight: 600 }}>
                        {inv.patient.user.firstName} {inv.patient.user.lastName}
                      </td>
                      <td>INR {inv.totalAmount}</td>
                      <td>INR {inv.taxAmount}</td>
                      <td style={{ fontWeight: 700, color: 'var(--text-title)' }}>INR {inv.netAmount}</td>
                      <td>{inv.dueDate}</td>
                      <td>
                        <span className={`badge ${inv.status === 'PAID' ? 'badge-success' : inv.status === 'PARTIALLY_PAID' ? 'badge-warning' : 'badge-danger'}`}>
                          {inv.status}
                        </span>
                      </td>
                      <td>
                        {inv.status !== 'PAID' && canBilling ? (
                          <button className="btn btn-primary" onClick={() => setActivePaymentInvoice(inv)} style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem' }}>
                            Record Payment
                          </button>
                        ) : (
                          <button className="btn btn-outline" style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem' }} onClick={() => window.print()}>
                            <Printer size={14} />
                          </button>
                        )}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={8} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>
                      No billing invoices found
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  headerText: {
    textAlign: 'left',
  },
  title: {
    fontSize: '1.75rem',
    fontWeight: 800,
  },
  subtitle: {
    fontSize: '0.9rem',
    color: 'var(--text-muted)',
    marginTop: '0.25rem',
  },
  errorBox: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
    padding: '0.75rem 1rem',
    borderRadius: 'var(--radius-sm)',
    fontSize: '0.875rem',
    fontWeight: 600,
  },
  listContainer: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1rem',
  },
  formCard: {
    textAlign: 'left',
    padding: '2.5rem',
  },
  formTitle: {
    fontSize: '1.25rem',
    fontWeight: 700,
    marginBottom: '1.5rem',
    color: 'var(--text-title)',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.25rem',
  },
  row: {
    display: 'flex',
    gap: '1.5rem',
  },
};
