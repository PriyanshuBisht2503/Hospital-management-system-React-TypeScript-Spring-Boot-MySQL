import { z } from 'zod';

export const generateInvoiceSchema = z.object({
  appointmentId: z.string().min(1, 'Appointment ID reference is required').refine((val) => {
    return /^\d+$/.test(val) && parseInt(val) > 0;
  }, 'Appointment ID must be a positive integer number'),
  fee: z.string().min(1, 'Consultation fee amount is required').refine((val) => {
    const parsed = parseFloat(val);
    return !isNaN(parsed) && parsed > 0;
  }, 'Consultation fee must be a positive fee amount'),
});

export const recordPaymentSchema = z.object({
  paymentMethod: z.enum(['CASH', 'CARD', 'UPI', 'INSURANCE']),
  transactionId: z.string().optional(),
});

export type GenerateInvoiceInput = z.infer<typeof generateInvoiceSchema>;
export type RecordPaymentInput = z.infer<typeof recordPaymentSchema>;
