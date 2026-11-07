import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { getPatient } from '../api/patients';
import { searchDoctors } from '../api/doctors';
import { bookAppointment } from '../api/appointments';
import { ApiError } from '../types/auth';
import { PatientSearch } from '../components/PatientSearch';
import { SlotPicker } from '../components/SlotPicker';
import { formatIstDateTime, toIstDateString } from '../lib/time';
import { VISIBLE_APPOINTMENT_TYPES } from '../types/appointment';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { Button, Card, Input, Select, useToast } from '@hms/ui';
import type { Patient } from '../types/patient';
import type { DoctorSearchResult, AvailableSlot } from '../types/doctor';
import type { AppointmentType } from '../types/appointment';

const TYPE_OPTIONS = VISIBLE_APPOINTMENT_TYPES.map((t) => ({ value: t, label: t === 'IN_PERSON' ? 'In person' : t === 'WALK_IN' ? 'Walk-in' : 'Follow-up' }));

const STEPS = ['Patient', 'Doctor', 'Date', 'Slot', 'Confirm'];

export const BookingWizardPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [searchParams] = useSearchParams();
  const preselectedPatientId = searchParams.get('patientId') ? Number(searchParams.get('patientId')) : undefined;
  const preselectedDoctorId = searchParams.get('doctorId') ? Number(searchParams.get('doctorId')) : undefined;
  const preselectedBranchId = searchParams.get('branchId') ? Number(searchParams.get('branchId')) : undefined;
  const preselectedDate = searchParams.get('date') ?? '';
  useDocumentTitle('Book Appointment');

  const [step, setStep] = useState(0);
  const [patient, setPatient] = useState<Patient | null>(null);
  const [doctor, setDoctor] = useState<DoctorSearchResult | null>(null);
  const [branchId, setBranchId] = useState<number | ''>('');
  const [date, setDate] = useState('');
  const [slot, setSlot] = useState<AvailableSlot | null>(null);
  const [type, setType] = useState<AppointmentType>('IN_PERSON');
  const [reason, setReason] = useState('');
  const [conflictError, setConflictError] = useState<string | null>(null);

  const today = toIstDateString(new Date());

  const preselectedQuery = useQuery({
    queryKey: ['preselect-patient', preselectedPatientId],
    queryFn: () => getPatient(preselectedPatientId!),
    enabled: preselectedPatientId != null,
  });

  React.useEffect(() => {
    if (preselectedQuery.data && patient == null) {
      setPatient(preselectedQuery.data);
      setStep(1);
    }
  }, [preselectedQuery.data, patient]);

  const doctorsQuery = useQuery({ queryKey: ['booking-doctors'], queryFn: () => searchDoctors({ size: 100 }) });

  // Preselect doctor/branch/date when opened from the day agenda "Book" link.
  React.useEffect(() => {
    if (doctor || !doctorsQuery.data || preselectedDoctorId == null) return;
    const found = doctorsQuery.data.content.find((d) => d.id === preselectedDoctorId);
    if (!found) return;
    setDoctor(found);
    if (preselectedBranchId) setBranchId(preselectedBranchId);
    if (preselectedDate) setDate(preselectedDate);
  }, [doctor, doctorsQuery.data, preselectedDoctorId, preselectedBranchId, preselectedDate]);

  const bookMutation = useMutation({ mutationFn: (body: Parameters<typeof bookAppointment>[0]) => bookAppointment(body) });

  const handleBook = async () => {
    if (!patient || !doctor || !branchId || !slot) return;
    setConflictError(null);
    try {
      const created = await bookMutation.mutateAsync({
        patientId: patient.id,
        doctorId: doctor.id,
        branchId: Number(branchId),
        startTime: slot.startTime,
        endTime: slot.endTime,
        type,
        reasonForVisit: reason || undefined,
      });
      toast.success('Appointment booked');
      navigate(`/appointments/${created.id}`);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setSlot(null);
        setConflictError('That slot was just taken. Please choose another slot.');
        queryClient.invalidateQueries({ queryKey: ['doctor-slots', doctor.id, branchId, date] });
        setStep(3);
      } else if (err instanceof ApiError) {
        toast.error(err.message);
      } else {
        toast.error('Failed to book appointment');
      }
    }
  };

  const canNext = step === 1 ? !!doctor && !!branchId : step === 2 ? !!date && date >= today : true;

  return (
    <div style={{ maxWidth: '640px' }}>
      <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', margin: '0 0 8px' }}>Book Appointment</h1>
      <div style={{ fontSize: '14px', color: '#334155', margin: '0 0 12px' }}>
        Patient: <strong>{patient?.fullName ?? 'Not selected'}</strong>
      </div>
      <Card style={{ padding: '24px' }}>
        <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
          {STEPS.map((label, i) => (
            <div key={label} style={{ flex: 1, textAlign: 'center', padding: '8px 4px', borderRadius: '8px', backgroundColor: step === i ? '#0d9488' : '#f1f5f9', color: step === i ? '#fff' : '#64748b', fontSize: '12px', fontWeight: 600 }}>
              {label}
            </div>
          ))}
        </div>

        {step === 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <PatientSearch placeholder="Search patient..." onSelect={(p) => { setPatient(p); setStep(doctor ? 3 : 1); }} />
            <Link to="/patients/new?returnTo=/appointments/new">Create a new walk-in patient</Link>
            {patient && <div style={{ fontSize: '14px' }}>Selected: <strong>{patient.fullName}</strong></div>}
          </div>
        )}

        {step === 1 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <Select label="Doctor" placeholder="Select doctor" options={(doctorsQuery.data?.content ?? []).map((d) => ({ value: d.id, label: d.fullName }))} value={doctor?.id ?? ''} onChange={(e) => { setDoctor(doctorsQuery.data?.content.find((x) => x.id === Number(e.target.value)) ?? null); setBranchId(''); }} />
            {doctor && (
              <Select label="Branch" placeholder="Select branch" options={doctor.branches.map((b) => ({ value: b.id, label: b.name }))} value={branchId} onChange={(e) => setBranchId(e.target.value ? Number(e.target.value) : '')} />
            )}
          </div>
        )}

        {step === 2 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <Input label="Date (IST)" type="date" value={date} onChange={(e) => setDate(e.target.value)} min={today} />
            {date && date < today && <div style={{ color: '#ef4444', fontSize: '13px' }}>Date cannot be in the past.</div>}
          </div>
        )}

        {step === 3 && (
          <div>
            {conflictError && <div style={{ color: '#ef4444', fontSize: '13px', marginBottom: '8px' }}>{conflictError}</div>}
            {doctor && branchId && date ? (
              <SlotPicker doctorId={doctor.id} branchId={Number(branchId)} date={date} selected={slot} onSelect={(s) => { setSlot(s); setStep(4); }} />
            ) : (
              <div style={{ color: '#64748b', fontSize: '14px' }}>Select doctor, branch and date first.</div>
            )}
          </div>
        )}

        {step === 4 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div style={{ fontSize: '14px', color: '#334155', lineHeight: 1.6 }}>
              <div>Patient: <strong>{patient?.fullName}</strong></div>
              <div>Doctor: <strong>{doctor?.fullName}</strong></div>
              <div>Branch: <strong>{doctor?.branches.find((b) => b.id === Number(branchId))?.name}</strong></div>
              <div>Slot: <strong>{slot ? formatIstDateTime(slot.startTime) : '—'}</strong></div>
            </div>
            <Select label="Type" options={TYPE_OPTIONS} value={type} onChange={(e) => setType(e.target.value as AppointmentType)} />
            <Input label="Reason for visit" value={reason} onChange={(e) => setReason(e.target.value)} />
            <Button variant="primary" size="md" disabled={bookMutation.isPending} onClick={handleBook}>
              {bookMutation.isPending ? 'Booking...' : 'Book Appointment'}
            </Button>
          </div>
        )}

        {step > 0 && step < 4 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '16px' }}>
            <Button variant="outline" size="sm" onClick={() => setStep(step - 1)}>Back</Button>
            <Button variant="primary" size="sm" disabled={!canNext} onClick={() => setStep(step + 1)}>Next</Button>
          </div>
        )}
      </Card>
    </div>
  );
};
