import React from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button, Input, Select } from '@hms/ui';
import { timeToMinutes } from '../lib/localTime';
import type { ScheduleExceptionType } from '../types/schedule';

export const EXCEPTION_TYPES: { value: ScheduleExceptionType; label: string }[] = [
  { value: 'LEAVE', label: 'Leave' },
  { value: 'HOLIDAY', label: 'Holiday' },
  { value: 'EXTRA_HOURS', label: 'Extra hours' },
];

export const scheduleExceptionSchema = z
  .object({
    date: z.string().min(1, 'Date is required'),
    type: z.enum(['LEAVE', 'HOLIDAY', 'EXTRA_HOURS']),
    startTime: z.string().optional().or(z.literal('')),
    endTime: z.string().optional().or(z.literal('')),
    reason: z.string().optional(),
  })
  .refine((d) => (d.startTime ? !!d.endTime : !d.endTime), {
    message: 'Provide both start and end time, or neither',
    path: ['endTime'],
  })
  .refine((d) => !d.startTime || !d.endTime || timeToMinutes(d.startTime) < timeToMinutes(d.endTime), {
    message: 'End time must be after start time',
    path: ['endTime'],
  })
  .refine((d) => d.type !== 'EXTRA_HOURS' || (!!d.startTime && !!d.endTime), {
    message: 'Start and end time are required for extra hours',
    path: ['startTime'],
  });

export type ScheduleExceptionFormData = z.infer<typeof scheduleExceptionSchema>;

export interface ScheduleExceptionFormProps {
  onSubmit: (values: ScheduleExceptionFormData) => Promise<void>;
  onCancel: () => void;
  isSubmitting: boolean;
}

export const ScheduleExceptionForm: React.FC<ScheduleExceptionFormProps> = ({ onSubmit, onCancel, isSubmitting }) => {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<ScheduleExceptionFormData>({
    defaultValues: { date: '', type: 'LEAVE', startTime: '', endTime: '', reason: '' },
  });

  const submit = async (formData: ScheduleExceptionFormData) => {
    const validation = scheduleExceptionSchema.safeParse(formData);
    if (!validation.success) {
      for (const issue of validation.error.issues) {
        setError(issue.path[0] as keyof ScheduleExceptionFormData, { message: issue.message });
      }
      return;
    }
    try {
      await onSubmit(validation.data);
    } catch {
      // Parent handles 409 (ForceExceptionDialog) and other server errors.
    }
  };

  return (
    <form onSubmit={handleSubmit(submit)} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
      <Input label="Date" type="date" error={errors.date?.message} {...register('date')} />
      <Select label="Type" options={EXCEPTION_TYPES} error={errors.type?.message} {...register('type')} />

      <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: '140px' }}>
          <Input label="Start Time (optional)" type="time" error={errors.startTime?.message} {...register('startTime')} />
        </div>
        <div style={{ flex: 1, minWidth: '140px' }}>
          <Input label="End Time (optional)" type="time" error={errors.endTime?.message} {...register('endTime')} />
        </div>
      </div>

      <Input label="Reason" placeholder="e.g. Annual leave" error={errors.reason?.message} {...register('reason')} />

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
        <Button type="button" variant="outline" size="md" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" size="md" disabled={isSubmitting}>
          {isSubmitting ? 'Saving...' : 'Create Exception'}
        </Button>
      </div>
    </form>
  );
};
