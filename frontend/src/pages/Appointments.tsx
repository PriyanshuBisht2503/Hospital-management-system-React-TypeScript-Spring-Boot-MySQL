import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { Calendar, Clock, ShieldAlert, CheckCircle } from 'lucide-react';

interface Appointment {
  id: number;
  patient: {
    id: number;
    user: { firstName: string; lastName: string };
  };
  doctor: {
    id: number;
    user: { firstName: string; lastName: string };
    specialization: string;
  };
  appointmentDate: string;
  timeSlot: string;
  status: string;
  tokenNumber: number;
}

interface Doctor {
  id: number;
  user: { firstName: string; lastName: string };
  specialization: string;
}

export const Appointments: React.FC = () => {
  const { user } = useAuth();
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [showAddForm, setShowAddForm] = useState(false);

  // Booking Form State
  const [patientId, setPatientId] = useState('');
  const [doctorId, setDoctorId] = useState('');
  const [appointmentDate, setAppointmentDate] = useState('');
  const [timeSlot, setTimeSlot] = useState('09:00:00');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const isPatient = user?.roles.includes('ROLE_PATIENT');

  const loadAppointments = async () => {
    try {
      if (isPatient) {
        // Patient role: fetch personal queue appointments. For demo, we fallback to patientId=1
        const res = await api.get('/appointments/patient/1');
        setAppointments(res.data.content || []);
      } else {
        // Staff/Doctor role: fetch doctor schedules. For demo, we query doctorId=1
        const res = await api.get('/appointments/doctor/1');
        setAppointments(res.data.content || []);
      }
    } catch (err) {
      setError('Failed to load appointments schedules');
    }
  };

  const loadDoctors = async () => {
    try {
      const res = await api.get('/doctors');
      setDoctors(res.data.content || []);
      if (res.data.content?.length > 0) {
        setDoctorId(res.data.content[0].id.toString());
      }
    } catch (err) {
      logError('Failed to fetch doctor specialization profiles');
    }
  };

  const logError = (msg: string) => {
    setError(msg);
  };

  useEffect(() => {
    loadAppointments();
    loadDoctors();
  }, []);

  const handleBookAppointment = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccess('');

    const payload = {
      patientId: isPatient ? 1 : parseInt(patientId), // Patient ID logic
      doctorId: parseInt(doctorId),
      appointmentDate,
      timeSlot,
    };

    try {
      const res = await api.post('/appointments', payload);
      setSuccess(`Appointment booked successfully! Token Number assigned: ${res.data.tokenNumber}`);
      setShowAddForm(false);
      loadAppointments();
      setPatientId('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Conflict: Target slot is already booked for this doctor');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelAppointment = async (id: number) => {
    if (!window.confirm('Are you sure you want to cancel this appointment schedule slot?')) return;
    try {
      await api.patch(`/appointments/${id}/cancel`);
      loadAppointments();
    } catch (err) {
      setError('Could not cancel appointment.');
    }
  };

  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <div style={styles.headerText}>
          <h1 style={styles.title}>Bookings & Scheduling</h1>
          <p style={styles.subtitle}>Coordinate outpatient slots and doctor schedules.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowAddForm(!showAddForm)}>
          <Calendar size={18} />
          <span>{showAddForm ? 'View Schedule' : 'Book Appointment'}</span>
        </button>
      </header>

      {error && (
        <div className="badge-danger" style={styles.errorBox}>
          <ShieldAlert size={18} />
          <span>{error}</span>
        </div>
      )}

      {success && (
        <div className="badge-success" style={styles.errorBox}>
          <CheckCircle size={18} />
          <span>{success}</span>
        </div>
      )}

      {showAddForm ? (
        <div className="card" style={styles.formCard}>
          <h3 style={styles.formTitle}>Schedule Appointment Slot</h3>
          <form onSubmit={handleBookAppointment} style={styles.form}>
            {!isPatient && (
              <div className="form-group">
                <label className="form-label">Patient ID</label>
                <input
                  type="number"
                  className="form-input"
                  placeholder="Enter registered patient numeric ID..."
                  value={patientId}
                  onChange={e => setPatientId(e.target.value)}
                  required
                />
              </div>
            )}

            <div className="form-group">
              <label className="form-label">Select Practitioner (Doctor)</label>
              <select className="form-input" value={doctorId} onChange={e => setDoctorId(e.target.value)}>
                {doctors.map(d => (
                  <option key={d.id} value={d.id}>
                    Dr. {d.user.firstName} {d.user.lastName} ({d.specialization})
                  </option>
                ))}
              </select>
            </div>

            <div style={styles.row}>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Appointment Date</label>
                <input
                  type="date"
                  className="form-input"
                  value={appointmentDate}
                  onChange={e => setAppointmentDate(e.target.value)}
                  required
                />
              </div>

              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Time Slot</label>
                <select className="form-input" value={timeSlot} onChange={e => setTimeSlot(e.target.value)}>
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
              </div>
            </div>

            <button type="submit" className="btn btn-primary" style={{ marginTop: '1rem' }} disabled={loading}>
              {loading ? 'Securing slot locks...' : 'Confirm Appointment'}
            </button>
          </form>
        </div>
      ) : (
        <div style={styles.listContainer}>
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
                        <span style={styles.tokenBadge}>#{a.tokenNumber}</span>
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
                        <div style={styles.timeLabel}>
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
                          <button className="btn btn-outline" onClick={() => handleCancelAppointment(a.id)} style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem' }}>
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
        </div>
      )}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  headerText: {
    textAlign: 'left',
  },
  title: {
    fontSize: '1.75rem',
    fontWeight: 800,
  },
  subtitle: {
    fontSize: '0.9rem',
    color: 'var(--text-muted)',
    marginTop: '0.25rem',
  },
  errorBox: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
    padding: '0.75rem 1rem',
    borderRadius: 'var(--radius-sm)',
    fontSize: '0.875rem',
    fontWeight: 600,
  },
  listContainer: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1rem',
  },
  formCard: {
    textAlign: 'left',
    padding: '2.5rem',
  },
  formTitle: {
    fontSize: '1.25rem',
    fontWeight: 700,
    marginBottom: '1.5rem',
    color: 'var(--text-title)',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.25rem',
  },
  row: {
    display: 'flex',
    gap: '1.5rem',
  },
  tokenBadge: {
    backgroundColor: 'var(--primary-light)',
    color: 'var(--primary)',
    fontWeight: 800,
    padding: '0.35rem 0.75rem',
    borderRadius: '4px',
    fontSize: '0.85rem',
  },
  timeLabel: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
    fontSize: '0.9rem',
    fontWeight: 600,
    color: 'var(--text-main)',
  },
};
