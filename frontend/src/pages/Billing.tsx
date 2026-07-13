import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuth } from '../context/AuthContext';
import { CreditCard, Printer, ShieldAlert, CheckCircle } from 'lucide-react';
import type { Invoice } from '../types';
import { 
  generateInvoiceSchema, 
  recordPaymentSchema, 
} from '../features/billing/billingSchemas';
import type {
  GenerateInvoiceInput, 
  RecordPaymentInput 
} from '../features/billing/billingSchemas';
import { 
  useInvoicesQuery, 
  useGenerateInvoiceMutation, 
  useRecordPaymentMutation 
} from '../features/billing/useBilling';
import { Skeleton } from '../components/ui/Skeleton';
import { ErrorBoundaryWrapper } from '../components/ui/ErrorBoundaryWrapper';
import styles from './Billing.module.css';

const BillingContent: React.FC = () => {
  const { user } = useAuth();
  const [showGenerateForm, setShowGenerateForm] = useState(false);
  const [activePaymentInvoice, setActivePaymentInvoice] = useState<Invoice | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const canBilling = user?.roles.includes('ROLE_ADMIN') || user?.roles.includes('ROLE_RECEPTIONIST');

  // Load invoices registry (patientId=1 fallback default demo mapping)
  const invoicesQuery = useInvoicesQuery(1);
  const generateMutation = useGenerateInvoiceMutation(1);
  const paymentMutation = useRecordPaymentMutation(1);

  const invoices = invoicesQuery.data?.content || [];

  const invoiceForm = useForm<GenerateInvoiceInput>({
    resolver: zodResolver(generateInvoiceSchema),
    defaultValues: {
      fee: '500.00',
    },
  });

  const paymentForm = useForm<RecordPaymentInput>({
    resolver: zodResolver(recordPaymentSchema),
    defaultValues: {
      paymentMethod: 'CASH',
    },
  });

  const onGenerateSubmit = async (formData: GenerateInvoiceInput) => {
    setErrorMessage('');
    setSuccessMessage('');
    try {
      await generateMutation.mutateAsync({
        appointmentId: parseInt(formData.appointmentId),
        fee: parseFloat(formData.fee),
      });
      setSuccessMessage('Consultation invoice generated successfully!');
      setShowGenerateForm(false);
      invoiceForm.reset();
    } catch (err: any) {
      setErrorMessage(err.response?.data?.message || 'Invoicing failed. Verify appointment ID is active and not duplicate.');
    }
  };

  const onPaymentSubmit = async (formData: RecordPaymentInput) => {
    if (!activePaymentInvoice) return;
    setErrorMessage('');
    setSuccessMessage('');
    
    const payload = {
      invoiceId: activePaymentInvoice.id,
      amount: activePaymentInvoice.netAmount,
      paymentMethod: formData.paymentMethod,
      transactionId: formData.transactionId || 'TXN-' + Date.now(),
    };

    try {
      await paymentMutation.mutateAsync(payload);
      setSuccessMessage('Payment successfully processed & reconciled via gateway!');
      setActivePaymentInvoice(null);
      paymentForm.reset();
    } catch (err: any) {
      setErrorMessage(err.response?.data?.message || 'Payment processing failed');
    }
  };

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <div className={styles.headerText}>
          <h1 className={styles.title}>Invoicing & Payments</h1>
          <p className={styles.subtitle}>Track patient invoices, processing fees, and payment settlements.</p>
        </div>
        {canBilling && (
          <button className="btn btn-primary" onClick={() => {
            setShowGenerateForm(!showGenerateForm);
            setActivePaymentInvoice(null);
          }}>
            <CreditCard size={18} />
            <span>{showGenerateForm ? 'View Invoices' : 'Generate Invoice'}</span>
          </button>
        )}
      </header>

      {(errorMessage || invoicesQuery.isError) && (
        <div className={`badge-danger ${styles.errorBox}`}>
          <ShieldAlert size={18} />
          <span>{errorMessage || 'Failed to fetch billing invoices history'}</span>
        </div>
      )}

      {successMessage && (
        <div className={`badge-success ${styles.errorBox}`}>
          <CheckCircle size={18} />
          <span>{successMessage}</span>
        </div>
      )}

      {showGenerateForm ? (
        <div className={`card ${styles.formCard}`}>
          <h3 className={styles.formTitle}>Generate Consultation Invoice</h3>
          <form onSubmit={invoiceForm.handleSubmit(onGenerateSubmit)} className={styles.form}>
            <div className="form-group">
              <label className="form-label">Appointment ID</label>
              <input
                type="number"
                className="form-input"
                placeholder="Enter confirmed appointment reference ID..."
                {...invoiceForm.register('appointmentId')}
              />
              {invoiceForm.formState.errors.appointmentId && (
                <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                  {invoiceForm.formState.errors.appointmentId.message}
                </span>
              )}
            </div>

            <div className="form-group">
              <label className="form-label">Consultation Fee (INR)</label>
              <input
                type="number"
                className="form-input"
                {...invoiceForm.register('fee')}
              />
              {invoiceForm.formState.errors.fee && (
                <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                  {invoiceForm.formState.errors.fee.message}
                </span>
              )}
            </div>

            <button type="submit" className="btn btn-primary" style={{ marginTop: '1rem' }} disabled={invoiceForm.formState.isSubmitting || generateMutation.isPending}>
              {invoiceForm.formState.isSubmitting || generateMutation.isPending ? 'Processing tax equations...' : 'Generate Bill'}
            </button>
          </form>
        </div>
      ) : activePaymentInvoice ? (
        <div className={`card ${styles.formCard}`}>
          <h3 className={styles.formTitle}>Process Payment Gateway charge</h3>
          <p style={{ marginBottom: '1.5rem', color: 'var(--text-muted)' }}>
            Processing transaction for Invoice #{activePaymentInvoice.id} of Patient: {activePaymentInvoice.patient.user.firstName} {activePaymentInvoice.patient.user.lastName}.
            Net settlement amount: <strong>INR {activePaymentInvoice.netAmount}</strong> (includes 18% GST).
          </p>
          <form onSubmit={paymentForm.handleSubmit(onPaymentSubmit)} className={styles.form}>
            <div className="form-group">
              <label className="form-label">Payment Method</label>
              <select className="form-input" {...paymentForm.register('paymentMethod')}>
                <option value="CASH">Cash</option>
                <option value="CARD">Debit/Credit Card</option>
                <option value="UPI">UPI/Digital Transfer</option>
                <option value="INSURANCE">Insurance Settlement</option>
              </select>
              {paymentForm.formState.errors.paymentMethod && (
                <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                  {paymentForm.formState.errors.paymentMethod.message}
                </span>
              )}
            </div>

            <div className="form-group">
              <label className="form-label">Transaction reference ID (Optional)</label>
              <input
                type="text"
                className="form-input"
                placeholder="Auto-generated if blank..."
                {...paymentForm.register('transactionId')}
              />
            </div>

            <div className={styles.row}>
              <button type="submit" className="btn btn-primary" style={{ flex: 1 }} disabled={paymentForm.formState.isSubmitting || paymentMutation.isPending}>
                {paymentForm.formState.isSubmitting || paymentMutation.isPending ? 'Processing charge gateway...' : 'Submit Payment'}
              </button>
              <button type="button" className="btn btn-outline" style={{ flex: 1 }} onClick={() => setActivePaymentInvoice(null)}>
                Cancel
              </button>
            </div>
          </form>
        </div>
      ) : (
        <div className={styles.listContainer}>
          {invoicesQuery.isLoading ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '1rem' }}>
              <Skeleton height="40px" />
              <Skeleton height="50px" />
              <Skeleton height="50px" />
            </div>
          ) : (
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
          )}
        </div>
      )}
    </div>
  );
};

export const Billing: React.FC = () => {
  return (
    <ErrorBoundaryWrapper name="BillingFeature">
      <BillingContent />
    </ErrorBoundaryWrapper>
  );
};
export default Billing;
