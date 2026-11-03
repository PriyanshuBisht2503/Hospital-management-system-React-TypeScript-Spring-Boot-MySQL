import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { createPatient } from '../api/patients';
import { ApiError } from '../types/auth';
import type { Gender } from '../types/patient';
import { Button, Card, Input, Select, useToast } from '@hms/ui';
import { ArrowLeft } from 'lucide-react';

export function isValidEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

export function isValidDateOfBirth(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const d = new Date(`${value}T00:00:00Z`);
  if (Number.isNaN(d.getTime())) return false;
  if (d.toISOString().slice(0, 10) !== value) return false;
  return d.getTime() <= Date.now();
}

export const createPatientSchema = z
  .object({
    fullName: z.string().trim().min(2, 'Full name is required'),
    phone: z.string().trim().optional(),
    email: z.string().trim().optional(),
    dateOfBirth: z.string().trim().optional(),
    gender: z.enum(['MALE', 'FEMALE', 'OTHER']).or(z.literal('')).optional(),
  })
  .refine((data) => !!(data.phone || data.email), {
    message: 'Provide at least a phone number or email address',
    path: ['phone'],
  })
  .refine((data) => !data.email || isValidEmail(data.email), {
    message: 'Please enter a valid email address',
    path: ['email'],
  })
  .refine((data) => !data.dateOfBirth || isValidDateOfBirth(data.dateOfBirth), {
    message: 'Enter a valid date of birth (not in the future)',
    path: ['dateOfBirth'],
  });

export type CreatePatientFormData = z.infer<typeof createPatientSchema>;

const GENDER_OPTIONS = [
  { value: 'MALE', label: 'Male' },
  { value: 'FEMALE', label: 'Female' },
  { value: 'OTHER', label: 'Other' },
];

export const NewPatientPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreatePatientFormData>();

  const mutation = useMutation({
    mutationFn: (data: CreatePatientFormData) =>
      createPatient({
        fullName: data.fullName,
        phone: data.phone || undefined,
        email: data.email || undefined,
        dateOfBirth: data.dateOfBirth || undefined,
        gender: (data.gender || undefined) as Gender | undefined,
      }),
    onSuccess: (created) => {
      toast.success(`Patient ${created.fullName} registered`);
      queryClient.invalidateQueries({ queryKey: ['patients'] });
      navigate(`/patients/${created.id}`);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.fieldErrors) {
        Object.entries(err.fieldErrors).forEach(([field, msg]) => {
          setError(field as keyof CreatePatientFormData, { message: msg });
        });
      } else if (err instanceof ApiError) {
        toast.error(err.message);
      } else {
        toast.error('Failed to register patient');
      }
    },
  });

  const onSubmit = async (formData: CreatePatientFormData) => {
    const validation = createPatientSchema.safeParse(formData);
    if (!validation.success) {
      for (const issue of validation.error.issues) {
        setError(issue.path[0] as keyof CreatePatientFormData, { message: issue.message });
      }
      return;
    }
    await mutation.mutateAsync(validation.data);
  };

  return (
    <div style={{ maxWidth: '640px' }}>
      <div style={{ marginBottom: '24px' }}>
        <Button variant="outline" size="sm" onClick={() => navigate('/patients')} style={{ marginBottom: '12px' }}>
          <ArrowLeft size={14} />
          Back to Patients
        </Button>
        <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>New Walk-in Patient</h1>
        <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>
          Register a patient at the front desk. Provide a phone number or email so we can reach them.
        </p>
      </div>

      <Card style={{ padding: '24px' }}>
        <form onSubmit={handleSubmit(onSubmit)} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <Input
            label="Full Name"
            placeholder="e.g. John Smith"
            error={errors.fullName?.message}
            {...register('fullName')}
          />

          <Input
            label="Phone Number"
            type="tel"
            placeholder="e.g. +971501234567"
            error={errors.phone?.message}
            {...register('phone')}
          />

          <Input
            label="Email Address"
            type="email"
            placeholder="e.g. patient@example.com"
            error={errors.email?.message}
            {...register('email')}
          />

          <Input
            label="Date of Birth"
            type="date"
            error={errors.dateOfBirth?.message}
            {...register('dateOfBirth')}
          />

          <Select
            label="Gender"
            placeholder="Select (optional)"
            options={GENDER_OPTIONS}
            error={errors.gender?.message}
            {...register('gender')}
          />

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
            <Button type="button" variant="outline" size="md" onClick={() => navigate('/patients')} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="md" disabled={isSubmitting}>
              {isSubmitting ? 'Saving...' : 'Register Patient'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
};
