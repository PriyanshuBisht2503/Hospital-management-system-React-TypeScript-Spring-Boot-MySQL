import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuth } from '../context/AuthContext';
import { Calendar, Clock, ShieldAlert, CheckCircle } from 'lucide-react';
import { appointmentBookingSchema } from '../features/appointments/appointmentSchemas';
import type { AppointmentBookingInput } from '../features/appointments/appointmentSchemas';
import { 
  usePatientAppointmentsQuery, 
  useDoctorAppointmentsQuery, 
  useCreateAppointmentMutation, 
  useCancelAppointmentMutation 
} from '../features/appointments/useAppointments';
import { useDoctorsQuery } from '../features/doctors/useDoctors';
import { Skeleton } from '../components/ui/Skeleton';
import { ErrorBoundaryWrapper } from '../components/ui/ErrorBoundaryWrapper';
import styles from './Appointments.module.css';

const AppointmentsContent: React.FC = () => {
  const { user } = useAuth();
  const [showAddForm, setShowAddForm] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const isPatient = user?.roles.includes('ROLE_PATIENT');
  
  // Set up standard query parameters for default clinic operations mappings (patientId=1, doctorId=1 fallbacks)
  const queryFilter = isPatient ? { patientId: 1 } : { doctorId: 1 };
  
  const patientAppointments = usePatientAppointmentsQuery(1, !!isPatient);
  const doctorAppointments = useDoctorAppointmentsQuery(1, !isPatient);
  const appointmentsQuery = isPatient ? patientAppointments : doctorAppointments;

  const doctorsQuery = useDoctorsQuery();
  const createMutation = useCreateAppointmentMutation();
  const cancelMutation = useCancelAppointmentMutation(queryFilter);

  const appointments = appointmentsQuery.data?.content || [];
  const doctors = doctorsQuery.data?.content || [];

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AppointmentBookingInput>({
    resolver: zodResolver(appointmentBookingSchema),
    defaultValues: {
      timeSlot: '09:00:00',
    },
  });

  const onSubmit = async (formData: AppointmentBookingInput) => {
    setErrorMessage('');
    setSuccessMessage('');
    
    const payload = {
      patientId: isPatient ? 1 : parseInt(formData.patientId || ''),
      doctorId: parseInt(formData.doctorId),
      appointmentDate: formData.appointmentDate,
      timeSlot: formData.timeSlot,
    };

    try {
      const res = await createMutation.mutateAsync(payload);
      setSuccessMessage(`Appointment booked successfully! Token Number assigned: #${res.tokenNumber}`);
      setShowAddForm(false);
      reset();
    } catch (err: any) {
      setErrorMessage(err.response?.data?.message || 'Conflict: Target slot is already booked for this doctor');
    }
  };

  const handleCancelAppointment = async (id: number) => {
    if (!window.confirm('Are you sure you want to cancel this appointment schedule slot?')) return;
    setErrorMessage('');
    try {
      await cancelMutation.mutateAsync(id);
    } catch (err: any) {
      setErrorMessage(err.response?.data?.message || 'Could not cancel appointment.');
    }
  };

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <div className={styles.headerText}>
          <h1 className={styles.title}>Bookings & Scheduling</h1>
          <p className={styles.subtitle}>Coordinate outpatient slots and doctor schedules.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowAddForm(!showAddForm)}>
          <Calendar size={18} />
          <span>{showAddForm ? 'View Schedule' : 'Book Appointment'}</span>
        </button>
      </header>

      {(errorMessage || appointmentsQuery.isError) && (
        <div className={`badge-danger ${styles.errorBox}`}>
          <ShieldAlert size={18} />
          <span>{errorMessage || 'Failed to retrieve scheduling entries'}</span>
        </div>
      )}

      {successMessage && (
        <div className={`badge-success ${styles.errorBox}`}>
          <CheckCircle size={18} />
          <span>{successMessage}</span>
        </div>
      )}

      {showAddForm ? (
        <div className={`card ${styles.formCard}`}>
          <h3 className={styles.formTitle}>Schedule Appointment Slot</h3>
          <form onSubmit={handleSubmit(onSubmit)} className={styles.form}>
            {!isPatient && (
              <div className="form-group">
                <label className="form-label">Patient ID</label>
                <input
                  type="number"
                  className="form-input"
                  placeholder="Enter registered patient numeric ID..."
                  {...register('patientId')}
                />
                {errors.patientId && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.patientId.message}</span>}
              </div>
            )}

            <div className="form-group">
              <label className="form-label">Select Practitioner (Doctor)</label>
              {doctorsQuery.isLoading ? (
                <Skeleton height="38px" />
              ) : (
                <select className="form-input" {...register('doctorId')}>
                  <option value="">-- Choose Practitioner --</option>
                  {doctors.map(d => (
                    <option key={d.id} value={d.id}>
                      Dr. {d.user.firstName} {d.user.lastName} ({d.specialization})
                    </option>
                  ))}
                </select>
              )}
              {errors.doctorId && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.doctorId.message}</span>}
            </div>

            <div className={styles.row}>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Appointment Date</label>
                <input
                  type="date"
                  className="form-input"
                  {...register('appointmentDate')}
                />
                {errors.appointmentDate && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.appointmentDate.message}</span>}
              </div>

              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Time Slot</label>
                <select className="form-input" {...register('timeSlot')}>
                  <option value="09:00:00">09:00 AM</option>
                  <option value="09:30:00">09:30 AM</option>
                  <option value="10:00:00">10:00 AM</option>
                  <option value="10:30:00">10:30 AM</option>
                  <option value="11:00:00">11:00 AM</option>
                  <option value="11:30:00">11:30 AM</option>
                  <option value="14:00:00">02:00 PM</option>
                  <option value="14:30:00">02:30 PM</option>
                  <option value="15:00:00">03:00 PM</option>
                  <option value="15:30:00">03:30 PM</option>
                </select>
                {errors.timeSlot && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.timeSlot.message}</span>}
              </div>
            </div>

            <button type="submit" className="btn btn-primary" style={{ marginTop: '1rem' }} disabled={isSubmitting || createMutation.isPending}>
              {isSubmitting || createMutation.isPending ? 'Securing slot locks...' : 'Confirm Appointment'}
            </button>
          </form>
        </div>
      ) : (
        <div className={styles.listContainer}>
          {appointmentsQuery.isLoading ? (
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
                    <th>Queue Token</th>
                    <th>Patient</th>
                    <th>Doctor</th>
                    <th>Specialization</th>
                    <th>Date</th>
                    <th>Time Slot</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {appointments.length > 0 ? (
                    appointments.map(a => (
                      <tr key={a.id}>
                        <td>
                          <span className={styles.tokenBadge}>#{a.tokenNumber}</span>
                        </td>
                        <td style={{ fontWeight: 600 }}>
                          {a.patient.user.firstName} {a.patient.user.lastName}
                        </td>
                        <td>
                          Dr. {a.doctor.user.firstName} {a.doctor.user.lastName}
                        </td>
                        <td>{a.doctor.specialization}</td>
                        <td>{a.appointmentDate}</td>
                        <td>
                          <div className={styles.timeLabel}>
                            <Clock size={14} />
                            <span>{a.timeSlot}</span>
                          </div>
                        </td>
                        <td>
                          <span className={`badge ${a.status === 'CONFIRMED' ? 'badge-success' : 'badge-danger'}`}>
                            {a.status}
                          </span>
                        </td>
                        <td>
                          {a.status !== 'CANCELLED' && (
                            <button 
                              className="btn btn-outline" 
                              onClick={() => handleCancelAppointment(a.id)} 
                              style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem' }}
                              disabled={cancelMutation.isPending}
                            >
                              Cancel
                            </button>
                          )}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={8} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>
                        No active appointment schedules logged for today
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

export const Appointments: React.FC = () => {
  return (
    <ErrorBoundaryWrapper name="AppointmentsFeature">
      <AppointmentsContent />
    </ErrorBoundaryWrapper>
  );
};
export default Appointments;
