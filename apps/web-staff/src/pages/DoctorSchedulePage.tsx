import React, { useState } from 'react';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getDoctor } from '../api/doctors';
import {
  listDoctorSchedules,
  createDoctorSchedule,
  updateDoctorSchedule,
  deleteDoctorSchedule,
  listScheduleExceptions,
  createScheduleException,
  deleteScheduleException,
} from '../api/schedules';
import { ApiError } from '../types/auth';
import { toIstDateString } from '../lib/time';
import type { DayOfWeek, DoctorSchedule, ScheduleException, CreateScheduleExceptionRequest } from '../types/schedule';
import { Button, Card, Modal, ConfirmDialog, Badge, useToast } from '@hms/ui';
import { ScheduleTemplateForm, ScheduleTemplateFormData } from '../components/ScheduleTemplateForm';
import { ScheduleExceptionForm, ScheduleExceptionFormData } from '../components/ScheduleExceptionForm';
import { ForceExceptionDialog } from '../components/ForceExceptionDialog';
import { Plus, ArrowLeft, Trash2, Edit2 } from 'lucide-react';

const DAYS_OF_WEEK: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

const typeLabel = (t: ScheduleException['type']) => (t === 'EXTRA_HOURS' ? 'Extra hours' : t === 'LEAVE' ? 'Leave' : 'Holiday');

export const DoctorSchedulePage: React.FC = () => {
  useDocumentTitle('Schedule');
  const { doctorId } = useParams<{ doctorId: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const numericId = Number(doctorId);
  const validId = Number.isInteger(numericId) && numericId > 0;
  const role = user?.role;
  const isDoctor = role === 'DOCTOR';
  const isAdmin = role === 'ADMIN';
  const canEditTemplates = isAdmin;
  const canEditExceptions = isAdmin || isDoctor;

  const [templateModal, setTemplateModal] = useState<{ open: boolean; editing: DoctorSchedule | null }>({ open: false, editing: null });
  const [exceptionModalOpen, setExceptionModalOpen] = useState(false);
  const [forceBody, setForceBody] = useState<CreateScheduleExceptionRequest | null>(null);
  const [deleteConflict, setDeleteConflict] = useState<{ schedule: DoctorSchedule; message: string } | null>(null);
  const [deleteExceptionTarget, setDeleteExceptionTarget] = useState<ScheduleException | null>(null);
  const [showPast, setShowPast] = useState(false);

  const canView = !isDoctor || (user?.doctorId != null && numericId === user.doctorId);

  const doctorQuery = useQuery({ queryKey: ['doctor', numericId], queryFn: () => getDoctor(numericId), enabled: validId && canView && !isDoctor });
  const schedulesQuery = useQuery({ queryKey: ['doctor-schedules', numericId], queryFn: () => listDoctorSchedules(numericId), enabled: validId && canView });
  const exceptionsQuery = useQuery({ queryKey: ['doctor-exceptions', numericId], queryFn: () => listScheduleExceptions(numericId), enabled: validId && canView });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['doctor-schedules', numericId] });
    queryClient.invalidateQueries({ queryKey: ['doctor-exceptions', numericId] });
  };

  const createTemplateMutation = useMutation({
    mutationFn: (body: Parameters<typeof createDoctorSchedule>[1]) => createDoctorSchedule(numericId, body),
    onSuccess: () => { invalidate(); setTemplateModal({ open: false, editing: null }); toast.success('Schedule template created'); },
    onError: (err: unknown) => toast.error(err instanceof ApiError ? err.message : 'Failed to create template'),
  });

  const updateTemplateMutation = useMutation({
    mutationFn: (body: { scheduleId: number; data: Parameters<typeof updateDoctorSchedule>[2] }) => updateDoctorSchedule(numericId, body.scheduleId, body.data),
    onSuccess: () => { invalidate(); setTemplateModal({ open: false, editing: null }); toast.success('Schedule template updated'); },
    onError: (err: unknown) => toast.error(err instanceof ApiError ? err.message : 'Failed to update template'),
  });

  const deleteTemplateMutation = useMutation({
    mutationFn: (scheduleId: number) => deleteDoctorSchedule(numericId, scheduleId),
    onSuccess: () => { invalidate(); setDeleteConflict(null); toast.success('Schedule deleted'); },
    onError: (err: unknown, scheduleId: number) => {
      if (err instanceof ApiError && err.status === 409) {
        const schedule = schedulesQuery.data?.find((s) => s.id === scheduleId) ?? null;
        if (schedule) setDeleteConflict({ schedule, message: err.message });
      } else {
        toast.error(err instanceof ApiError ? err.message : 'Failed to delete template');
      }
    },
  });

  const deactivateTemplateMutation = useMutation({
    mutationFn: (schedule: DoctorSchedule) =>
      updateDoctorSchedule(numericId, schedule.id, {
        branchId: schedule.branchId,
        dayOfWeek: schedule.dayOfWeek,
        startTime: schedule.startTime,
        endTime: schedule.endTime,
        slotMinutes: schedule.slotMinutes,
        breakStart: schedule.breakStart ?? undefined,
        breakEnd: schedule.breakEnd ?? undefined,
        validFrom: schedule.validFrom,
        validTo: schedule.validTo ?? undefined,
        isActive: false,
      }),
    onSuccess: () => { invalidate(); setDeleteConflict(null); toast.success('Schedule deactivated'); },
    onError: (err: unknown) => toast.error(err instanceof ApiError ? err.message : 'Failed to deactivate template'),
  });

  const createExceptionMutation = useMutation({
    mutationFn: (body: CreateScheduleExceptionRequest) => createScheduleException(numericId, body),
  });

  const deleteExceptionMutation = useMutation({
    mutationFn: (exceptionId: number) => deleteScheduleException(numericId, exceptionId),
    onSuccess: () => { invalidate(); setDeleteExceptionTarget(null); toast.success('Exception deleted'); },
    onError: (err: unknown) => toast.error(err instanceof ApiError ? err.message : 'Failed to delete exception'),
  });

  // Guards
  if (isDoctor) {
    if (user?.doctorId == null) {
      return (
        <Card style={{ maxWidth: '640px', margin: '0 auto', padding: '48px', textAlign: 'center' }}>
          <h1 style={{ fontSize: '20px', fontWeight: 700, color: '#0f172a', margin: '0 0 8px' }}>No doctor profile is linked to your account</h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>Ask an administrator to create a doctor profile for you to manage a schedule.</p>
        </Card>
      );
    }
    if (!validId || numericId !== user.doctorId) {
      return (
        <Card style={{ maxWidth: '640px', margin: '0 auto', padding: '48px', textAlign: 'center' }}>
          <h1 style={{ fontSize: '20px', fontWeight: 700, color: '#0f172a', margin: '0 0 8px' }}>Schedule not found</h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>This schedule does not exist or you do not have access to it.</p>
        </Card>
      );
    }
  }

  if (!validId) {
    return (
      <Card style={{ maxWidth: '640px', margin: '0 auto', padding: '48px', textAlign: 'center' }}>
        <h1 style={{ fontSize: '20px', fontWeight: 700, color: '#0f172a', margin: '0 0 8px' }}>Schedule not found</h1>
      </Card>
    );
  }

  const doctor = doctorQuery.data;
  const schedules = schedulesQuery.data ?? [];
  const exceptions = exceptionsQuery.data ?? [];

  const handleTemplateSubmit = async (values: ScheduleTemplateFormData) => {
    const base = {
      branchId: values.branchId,
      dayOfWeek: values.dayOfWeek,
      startTime: values.startTime,
      endTime: values.endTime,
      slotMinutes: values.slotMinutes,
      breakStart: values.breakStart || undefined,
      breakEnd: values.breakEnd || undefined,
      validFrom: values.validFrom,
      validTo: values.validTo || undefined,
    };
    if (templateModal.editing) {
      await updateTemplateMutation.mutateAsync({ scheduleId: templateModal.editing.id, data: { ...base, isActive: values.isActive } });
    } else {
      await createTemplateMutation.mutateAsync(base);
    }
  };

  const handleExceptionSubmit = async (values: ScheduleExceptionFormData) => {
    const body: CreateScheduleExceptionRequest = {
      date: values.date,
      type: values.type,
      startTime: values.startTime || undefined,
      endTime: values.endTime || undefined,
      reason: values.reason || undefined,
      force: false,
    };
    try {
      await createExceptionMutation.mutateAsync(body);
      invalidate();
      setExceptionModalOpen(false);
      toast.success('Exception created');
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setExceptionModalOpen(false);
        setForceBody(body);
      } else if (err instanceof ApiError) {
        toast.error(err.message);
      } else {
        toast.error('Failed to create exception');
      }
    }
  };

  const confirmForce = async () => {
    if (!forceBody) return;
    try {
      await createExceptionMutation.mutateAsync({ ...forceBody, force: true });
      invalidate();
      setForceBody(null);
      toast.success('Exception created');
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Failed to create exception');
    }
  };

  const today = toIstDateString(new Date());
  const sortedExceptions = [...exceptions].sort((a, b) => a.date.localeCompare(b.date));
  const upcoming = sortedExceptions.filter((e) => e.date >= today);
  const past = sortedExceptions.filter((e) => e.date < today).reverse();
  const visibleExceptions = showPast ? [...upcoming, ...past] : upcoming;

  return (
    <div>
      <Button variant="outline" size="sm" onClick={() => navigate('/schedules')} style={{ marginBottom: '16px' }}>
        <ArrowLeft size={14} />
        Back to Schedules
      </Button>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>
            {doctor?.fullName ?? `Doctor ${numericId}`} — Schedule
          </h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>Weekly templates and exceptions</p>
        </div>
        {canEditExceptions && (
          <Button variant="primary" size="md" onClick={() => setExceptionModalOpen(true)}>
            <Plus size={16} />
            Add Exception
          </Button>
        )}
      </div>

      {/* Templates */}
      <Card style={{ marginBottom: '20px' }}>
        <div style={{ padding: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 style={{ fontSize: '16px', fontWeight: 700, color: '#0f172a', margin: 0 }}>Weekly Templates</h2>
          {canEditTemplates && (
            <Button variant="secondary" size="sm" onClick={() => setTemplateModal({ open: true, editing: null })}>
              <Plus size={14} />
              Add Template
            </Button>
          )}
        </div>

        {schedulesQuery.isLoading ? (
          <div style={{ padding: '32px', textAlign: 'center', color: '#64748b' }}>Loading templates...</div>
        ) : schedulesQuery.isError ? (
          <div style={{ padding: '32px', textAlign: 'center', color: '#ef4444' }}>Failed to load templates</div>
        ) : schedules.length === 0 ? (
          <div style={{ padding: '32px', textAlign: 'center', color: '#64748b' }}>No templates defined yet.</div>
        ) : (
          <div style={{ padding: '0 16px 16px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {DAYS_OF_WEEK.map((day) => {
              const daySchedules = schedules.filter((s) => s.dayOfWeek === day);
              if (daySchedules.length === 0) return null;
              return (
                <div key={day}>
                  <div style={{ fontSize: '13px', fontWeight: 700, color: '#334155', marginBottom: '8px', textTransform: 'capitalize' }}>{day.toLowerCase()}</div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    {daySchedules.map((s) => (
                      <div key={s.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', flexWrap: 'wrap', padding: '10px 12px', borderRadius: '8px', border: '1px solid #e2e8f0', fontSize: '13px' }}>
                        <div>
                          <div style={{ fontWeight: 600, color: '#0f172a' }}>
                            {s.branchName ?? `Branch ${s.branchId}`} · {s.startTime}–{s.endTime}
                          </div>
                          <div style={{ color: '#64748b' }}>
                            {s.slotMinutes} min slots
                            {s.breakStart && s.breakEnd ? ` · break ${s.breakStart}–${s.breakEnd}` : ''}
                            {` · valid ${s.validFrom}${s.validTo ? ` to ${s.validTo}` : ''}`}
                          </div>
                        </div>
                        <div style={{ display: 'inline-flex', gap: '8px', alignItems: 'center' }}>
                          {!s.isActive && <Badge variant="neutral">Inactive</Badge>}
                          {canEditTemplates && (
                            <>
                              <Button variant="outline" size="sm" title="Edit template" onClick={() => setTemplateModal({ open: true, editing: s })}>
                                <Edit2 size={14} />
                              </Button>
                              <Button variant="outline" size="sm" title="Delete template" onClick={() => deleteTemplateMutation.mutate(s.id)}>
                                <Trash2 size={14} />
                              </Button>
                            </>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Card>

      {/* Exceptions */}
      <Card>
        <div style={{ padding: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
          <h2 style={{ fontSize: '16px', fontWeight: 700, color: '#0f172a', margin: 0 }}>Exceptions</h2>
          <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: '#334155' }}>
            <input type="checkbox" checked={showPast} onChange={(e) => setShowPast(e.target.checked)} />
            Show past
          </label>
        </div>

        {exceptionsQuery.isLoading ? (
          <div style={{ padding: '32px', textAlign: 'center', color: '#64748b' }}>Loading exceptions...</div>
        ) : exceptionsQuery.isError ? (
          <div style={{ padding: '32px', textAlign: 'center', color: '#ef4444' }}>Failed to load exceptions</div>
        ) : visibleExceptions.length === 0 ? (
          <div style={{ padding: '32px', textAlign: 'center', color: '#64748b' }}>{showPast ? 'No exceptions.' : 'No upcoming exceptions.'}</div>
        ) : (
          <div style={{ padding: '0 16px 16px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {visibleExceptions.map((e) => (
              <div key={e.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', flexWrap: 'wrap', padding: '10px 12px', borderRadius: '8px', border: '1px solid #e2e8f0', fontSize: '13px' }}>
                <div>
                  <div style={{ fontWeight: 600, color: '#0f172a' }}>
                    {e.date} · {typeLabel(e.type)}
                    {e.startTime && e.endTime ? ` · ${e.startTime}–${e.endTime}` : ' · all day'}
                  </div>
                  {e.reason && <div style={{ color: '#64748b' }}>{e.reason}</div>}
                </div>
                {canEditExceptions && (
                  <Button variant="outline" size="sm" title="Delete exception" onClick={() => setDeleteExceptionTarget(e)}>
                    <Trash2 size={14} />
                  </Button>
                )}
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* Template modal */}
      <Modal isOpen={templateModal.open} onClose={() => setTemplateModal({ open: false, editing: null })} title={templateModal.editing ? 'Edit Template' : 'Add Template'}>
        <ScheduleTemplateForm
          key={templateModal.editing?.id ?? 'new'}
          branches={doctor?.branches ?? []}
          existing={schedules}
          editing={templateModal.editing}
          onSubmit={handleTemplateSubmit}
          onCancel={() => setTemplateModal({ open: false, editing: null })}
          isSubmitting={createTemplateMutation.isPending || updateTemplateMutation.isPending}
        />
      </Modal>

      {/* Exception modal */}
      <Modal isOpen={exceptionModalOpen} onClose={() => setExceptionModalOpen(false)} title="Add Exception">
        <ScheduleExceptionForm
          onSubmit={handleExceptionSubmit}
          onCancel={() => setExceptionModalOpen(false)}
          isSubmitting={createExceptionMutation.isPending}
        />
      </Modal>

      {/* Force dialog */}
      <Modal isOpen={!!forceBody} onClose={() => setForceBody(null)} title="Affected Appointments">
        {forceBody && (
          <ForceExceptionDialog
            doctorId={numericId}
            date={forceBody.date}
            startTime={forceBody.startTime ?? null}
            endTime={forceBody.endTime ?? null}
            onConfirm={confirmForce}
            onCancel={() => setForceBody(null)}
            isSubmitting={createExceptionMutation.isPending}
          />
        )}
      </Modal>

      {/* Delete conflict dialog */}
      <Modal isOpen={!!deleteConflict} onClose={() => setDeleteConflict(null)} title="Cannot Delete Schedule">
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <p style={{ margin: 0, fontSize: '14px', color: '#334155' }}>{deleteConflict?.message}</p>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
            <Button variant="outline" size="md" onClick={() => setDeleteConflict(null)}>Close</Button>
            <Button
              variant="primary"
              size="md"
              disabled={deactivateTemplateMutation.isPending}
              onClick={() => deleteConflict && deactivateTemplateMutation.mutate(deleteConflict.schedule)}
            >
              {deactivateTemplateMutation.isPending ? 'Deactivating...' : 'Deactivate Instead'}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Delete exception confirm */}
      <ConfirmDialog
        isOpen={!!deleteExceptionTarget}
        title="Delete Exception"
        message={`Delete the ${deleteExceptionTarget ? typeLabel(deleteExceptionTarget.type).toLowerCase() : ''} exception on ${deleteExceptionTarget?.date}?`}
        confirmLabel="Delete Exception"
        variant="danger"
        isLoading={deleteExceptionMutation.isPending}
        onConfirm={() => deleteExceptionTarget && deleteExceptionMutation.mutate(deleteExceptionTarget.id)}
        onCancel={() => setDeleteExceptionTarget(null)}
      />
    </div>
  );
};
