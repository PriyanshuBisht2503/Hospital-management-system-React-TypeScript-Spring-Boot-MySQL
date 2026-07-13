import { z } from 'zod';

export const appointmentBookingSchema = z.object({
  patientId: z.string().optional().refine((val) => {
    // When provided, must be a numeric integer
    if (!val) return true;
    return /^\d+$/.test(val) && parseInt(val) > 0;
  }, 'Patient ID must be a positive integer number'),
  doctorId: z.string().min(1, 'Practitioner practitioner selection is required'),
  appointmentDate: z.string().min(1, 'Appointment date scheduling selection is required'),
  timeSlot: z.string().min(1, 'Time slot selection is required'),
});

export type AppointmentBookingInput = z.infer<typeof appointmentBookingSchema>;
