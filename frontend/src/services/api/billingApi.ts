import { api } from '../api';
import type { Invoice, PagedResponse } from '../../types';

export const billingApi = {
  getPatientInvoices: async (patientId: number): Promise<PagedResponse<Invoice>> => {
    const response = await api.get(`/billing/invoices/patient/${patientId}`);
    return response.data;
  },
  generateInvoice: async (appointmentId: number, fee: number): Promise<Invoice> => {
    const response = await api.post(`/billing/invoices/consultation?appointmentId=${appointmentId}&fee=${fee}`);
    return response.data;
  },
  recordPayment: async (payload: {
    invoiceId: number;
    amount: number;
    paymentMethod: string;
    transactionId: string;
  }): Promise<void> => {
    await api.post('/billing/payments', payload);
  },
};
