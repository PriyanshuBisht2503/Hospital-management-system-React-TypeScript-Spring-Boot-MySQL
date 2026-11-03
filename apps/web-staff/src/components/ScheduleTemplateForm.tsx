import React from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button, Input, Select } from '@hms/ui';
import type { Branch } from '../types/masterData';
import type { DayOfWeek, DoctorSchedule } from '../types/schedule';
import { stripSeconds, timeToMinutes } from '../lib/localTime';

export const SCHEDULE_DAYS: { value: DayOfWeek; label: string }[] = [
  { value: 'MONDAY', label: 'Monday' },
  { value: 'TUESDAY', label: 'Tuesday' },
  { value: 'WEDNESDAY', label: 'Wednesday' },
  { value: 'THURSDAY', label: 'Thursday' },
  { value: 'FRIDAY', label: 'Friday' },
  { value: 'SATURDAY', label: 'Saturday' },
  { value: 'SUNDAY', label: 'Sunday' },
];

const DAYS: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

export const scheduleTemplateSchema = z
  .object({
    branchId: z.coerce.number().min(1, 'Please select a branch'),
    dayOfWeek: z.enum(DAYS as [DayOfWeek, ...DayOfWeek[]]),
    startTime: z.string().min(1, 'Start time is required'),
    endTime: z.string().min(1, 'End time is required'),
    slotMinutes: z.coerce.number().min(5, 'Slot duration must be 5-120 minutes').max(120, 'Slot duration must be 5-120 minutes'),
    breakStart: z.string().optional().or(z.literal('')),
    breakEnd: z.string().optional().or(z.literal('')),
    validFrom: z.string().min(1, 'Valid from date is required'),
    validTo: z.string().optional().or(z.literal('')),
    isActive: z.boolean().optional(),
  })
  .refine((d) => timeToMinutes(d.startTime) < timeToMinutes(d.endTime), {
    message: 'End time must be after start time',
    path: ['endTime'],
  })
  .refine((d) => (d.breakStart ? !!d.breakEnd : !d.breakEnd), {
    message: 'Provide both break start and break end, or neither',
    path: ['breakEnd'],
  })
  .refine((d) => !d.breakStart || !d.breakEnd || timeToMinutes(d.breakStart) < timeToMinutes(d.breakEnd), {
    message: 'Break end must be after break start',
    path: ['breakEnd'],
  })
  .refine(
    (d) => {
      if (!d.breakStart || !d.breakEnd) return true;
      const s = timeToMinutes(d.startTime);
      const e = timeToMinutes(d.endTime);
      return timeToMinutes(d.breakStart) >= s && timeToMinutes(d.breakEnd) <= e;
    },
    { message: 'Break must fall within the working window', path: ['breakStart'] },
  )
  .refine((d) => !d.validTo || d.validTo >= d.validFrom, {
    message: 'Valid to must be on or after valid from',
    path: ['validTo'],
  });

export type ScheduleTemplateFormData = z.infer<typeof scheduleTemplateSchema>;

/** Client-side overlap check against the doctor's other active templates. Returns a message or null. */
export function findTemplateOverlap(
  candidate: { dayOfWeek: DayOfWeek; branchId: number; startTime: string; endTime: string; validFrom: string; validTo: string | null },
  existing: DoctorSchedule[],
  excludeId?: number,
): string | null {
  const cs = timeToMinutes(candidate.startTime);
  const ce = timeToMinutes(candidate.endTime);
  const cvTo = candidate.validTo ?? '9999-12-31';

  for (const s of existing) {
    if (excludeId != null && s.id === excludeId) continue;
    if (!s.isActive) continue;
    if (s.dayOfWeek !== candidate.dayOfWeek) continue;
    if (s.branchId !== candidate.branchId) continue;

    const timesOverlap = cs < timeToMinutes(s.endTime) && ce > timeToMinutes(s.startTime);
    const validityOverlap = candidate.validFrom <= (s.validTo ?? '9999-12-31') && cvTo >= s.validFrom;
    if (timesOverlap && validityOverlap) {
      return `Overlaps the existing ${s.dayOfWeek.toLowerCase()} template (${s.startTime}–${s.endTime}) in this branch`;
    }
  }
  return null;
}

export interface ScheduleTemplateFormProps {
  branches: Branch[];
  existing: DoctorSchedule[];
  editing: DoctorSchedule | null;
  onSubmit: (values: ScheduleTemplateFormData) => Promise<void>;
  onCancel: () => void;
  isSubmitting: boolean;
}

export const ScheduleTemplateForm: React.FC<ScheduleTemplateFormProps> = ({
  branches,
  existing,
  editing,
  onSubmit,
  onCancel,
  isSubmitting,
}) => {
  const {
    register,
    handleSubmit,
    setError,
    setValue,
    watch,
    formState: { errors },
  } = useForm<ScheduleTemplateFormData>({
    defaultValues: {
      branchId: editing?.branchId ?? 0,
      dayOfWeek: editing?.dayOfWeek ?? 'MONDAY',
      startTime: editing ? stripSeconds(editing.startTime) : '',
      endTime: editing ? stripSeconds(editing.endTime) : '',
      slotMinutes: editing?.slotMinutes ?? 30,
      breakStart: editing?.breakStart ? stripSeconds(editing.breakStart) : '',
      breakEnd: editing?.breakEnd ? stripSeconds(editing.breakEnd) : '',
      validFrom: editing?.validFrom ?? '',
      validTo: editing?.validTo ?? '',
      isActive: editing?.isActive ?? true,
    },
  });

  const [overlapError, setOverlapError] = React.useState<string | null>(null);
  const startTime = watch('startTime');
  const endTime = watch('endTime');

  const submit = async (formData: ScheduleTemplateFormData) => {
    setOverlapError(null);
    const validation = scheduleTemplateSchema.safeParse(formData);
    if (!validation.success) {
      for (const issue of validation.error.issues) {
        setError(issue.path[0] as keyof ScheduleTemplateFormData, { message: issue.message });
      }
      return;
    }

    const overlap = findTemplateOverlap(
      {
        dayOfWeek: validation.data.dayOfWeek,
        branchId: validation.data.branchId,
        startTime: validation.data.startTime,
        endTime: validation.data.endTime,
        validFrom: validation.data.validFrom,
        validTo: validation.data.validTo || null,
      },
      existing,
      editing?.id,
    );
    if (overlap) {
      setOverlapError(overlap);
      return;
    }

    try {
      await onSubmit(validation.data);
    } catch {
      // Handled by the parent mutation's onError (server fieldErrors/messages).
    }
  };

  return (
    <form onSubmit={handleSubmit(submit)} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
      {overlapError && (
        <div style={{ padding: '10px 14px', borderRadius: '8px', backgroundColor: '#fef2f2', color: '#991b1b', fontSize: '13px' }}>
          {overlapError}
        </div>
      )}

      <Select
        label="Branch"
        options={branches.map((b) => ({ value: b.id, label: b.name }))}
        error={errors.branchId?.message}
        {...register('branchId')}
      />

      <Select label="Day of Week" options={SCHEDULE_DAYS} error={errors.dayOfWeek?.message} {...register('dayOfWeek')} />

      <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: '140px' }}>
          <Input label="Start Time" type="time" error={errors.startTime?.message} {...register('startTime')} />
        </div>
        <div style={{ flex: 1, minWidth: '140px' }}>
          <Input label="End Time" type="time" error={errors.endTime?.message} {...register('endTime')} />
        </div>
        <div style={{ flex: 1, minWidth: '140px' }}>
          <Input label="Slot Minutes" type="number" min={5} max={120} error={errors.slotMinutes?.message} {...register('slotMinutes')} />
        </div>
      </div>

      <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: '140px' }}>
          <Input label="Break Start (optional)" type="time" error={errors.breakStart?.message} {...register('breakStart')} />
        </div>
        <div style={{ flex: 1, minWidth: '140px' }}>
          <Input label="Break End (optional)" type="time" error={errors.breakEnd?.message} {...register('breakEnd')} />
        </div>
      </div>

      <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: '140px' }}>
          <Input label="Valid From" type="date" error={errors.validFrom?.message} {...register('validFrom')} />
        </div>
        <div style={{ flex: 1, minWidth: '140px' }}>
          <Input label="Valid To (optional)" type="date" error={errors.validTo?.message} {...register('validTo')} />
        </div>
      </div>

      {editing && (
        <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '14px', color: '#334155' }}>
          <input type="checkbox" defaultChecked={editing.isActive} onChange={(e) => setValue('isActive', e.target.checked)} />
          Active
        </label>
      )}

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
        <Button type="button" variant="outline" size="md" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" size="md" disabled={isSubmitting || !startTime || !endTime}>
          {isSubmitting ? 'Saving...' : editing ? 'Update Template' : 'Create Template'}
        </Button>
      </div>
    </form>
  );
};
