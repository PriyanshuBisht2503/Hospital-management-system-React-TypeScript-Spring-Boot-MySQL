import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { Search, UserPlus, Trash2, ShieldAlert } from 'lucide-react';

interface Patient {
  id: number;
  user: {
    firstName: string;
    lastName: string;
    email: string;
    phone: string;
  };
  dateOfBirth: string;
  gender: string;
  bloodGroup: string;
}

export const Patients: React.FC = () => {
  const { user } = useAuth();
  const [patients, setPatients] = useState<Patient[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [showAddForm, setShowAddForm] = useState(false);

  // Form State
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [phone, setPhone] = useState('');
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [gender, setGender] = useState('MALE');
  const [bloodGroup, setBloodGroup] = useState('O+');
  const [address, setAddress] = useState('');
  const [emergencyContactName, setEmergencyContactName] = useState('');
  const [emergencyContactPhone, setEmergencyContactPhone] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const loadPatients = async () => {
    try {
      const res = await api.get(`/patients?query=${searchQuery}`);
      setPatients(res.data.content || []);
    } catch (err) {
      setError('Failed to load patients list');
    }
  };

  useEffect(() => {
    loadPatients();
  }, [searchQuery]);

  const handleRegisterPatient = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    const payload = {
      user: { email, password, firstName, lastName, phone },
      dateOfBirth,
      gender,
      bloodGroup,
      address,
      emergencyContactName,
      emergencyContactPhone,
    };

    try {
      await api.post('/patients', payload);
      setShowAddForm(false);
      loadPatients();
      // Clear form
      setEmail('');
      setPassword('');
      setFirstName('');
      setLastName('');
      setPhone('');
      setDateOfBirth('');
      setAddress('');
      setEmergencyContactName('');
      setEmergencyContactPhone('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to register patient profile');
    } finally {
      setLoading(false);
    }
  };

  const handleDeletePatient = async (id: number) => {
    if (!window.confirm('Are you sure you want to delete this patient record?')) return;
    try {
      await api.delete(`/patients/${id}`);
      loadPatients();
    } catch (err) {
      setError('Could not delete patient record. Ensure you have administrator rights.');
    }
  };

  const isAdmin = user?.roles.includes('ROLE_ADMIN');

  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <div style={styles.headerText}>
          <h1 style={styles.title}>Patient Directory</h1>
          <p style={styles.subtitle}>Manage and register clinic outpatient profiles.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowAddForm(!showAddForm)}>
          <UserPlus size={18} />
          <span>{showAddForm ? 'View Directory' : 'Register New Patient'}</span>
        </button>
      </header>

      {error && (
        <div className="badge-danger" style={styles.errorBox}>
          <ShieldAlert size={18} />
          <span>{error}</span>
        </div>
      )}

      {showAddForm ? (
        <div className="card" style={styles.formCard}>
          <h3 style={styles.formTitle}>Outpatient Registration Form</h3>
          <form onSubmit={handleRegisterPatient} style={styles.form}>
            <h4 style={styles.sectionDivider}>Account Credentials</h4>
            <div style={styles.row}>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Email Address</label>
                <input type="email" className="form-input" value={email} onChange={e => setEmail(e.target.value)} required />
              </div>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Password</label>
                <input type="password" className="form-input" value={password} onChange={e => setPassword(e.target.value)} required />
              </div>
            </div>

            <h4 style={styles.sectionDivider}>Personal Profile Details</h4>
            <div style={styles.row}>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">First Name</label>
                <input type="text" className="form-input" value={firstName} onChange={e => setFirstName(e.target.value)} required />
              </div>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Last Name</label>
                <input type="text" className="form-input" value={lastName} onChange={e => setLastName(e.target.value)} required />
              </div>
            </div>

            <div style={styles.row}>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Phone Number</label>
                <input type="tel" className="form-input" value={phone} onChange={e => setPhone(e.target.value)} required />
              </div>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Date of Birth</label>
                <input type="date" className="form-input" value={dateOfBirth} onChange={e => setDateOfBirth(e.target.value)} required />
              </div>
            </div>

            <div style={styles.row}>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Gender</label>
                <select className="form-input" value={gender} onChange={e => setGender(e.target.value)}>
                  <option value="MALE">Male</option>
                  <option value="FEMALE">Female</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Blood Group</label>
                <select className="form-input" value={bloodGroup} onChange={e => setBloodGroup(e.target.value)}>
                  <option value="A+">A+</option>
                  <option value="A-">A-</option>
                  <option value="B+">B+</option>
                  <option value="B-">B-</option>
                  <option value="AB+">AB+</option>
                  <option value="AB-">AB-</option>
                  <option value="O+">O+</option>
                  <option value="O-">O-</option>
                </select>
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Address</label>
              <textarea className="form-input" rows={2} value={address} onChange={e => setAddress(e.target.value)} required />
            </div>

            <h4 style={styles.sectionDivider}>Emergency Contacts</h4>
            <div style={styles.row}>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Contact Name</label>
                <input type="text" className="form-input" value={emergencyContactName} onChange={e => setEmergencyContactName(e.target.value)} required />
              </div>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Contact Phone</label>
                <input type="tel" className="form-input" value={emergencyContactPhone} onChange={e => setEmergencyContactPhone(e.target.value)} required />
              </div>
            </div>

            <button type="submit" className="btn btn-primary" style={{ marginTop: '1rem' }} disabled={loading}>
              {loading ? 'Submitting Registration...' : 'Register Profile'}
            </button>
          </form>
        </div>
      ) : (
        <div style={styles.listContainer}>
          <div style={styles.searchBar}>
            <Search size={20} color="var(--text-muted)" />
            <input
              type="text"
              className="form-input"
              placeholder="Search patients by name or email..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              style={styles.searchInput}
            />
          </div>

          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Phone</th>
                  <th>DOB</th>
                  <th>Gender</th>
                  <th>Blood</th>
                  {isAdmin && <th>Actions</th>}
                </tr>
              </thead>
              <tbody>
                {patients.length > 0 ? (
                  patients.map(p => (
                    <tr key={p.id}>
                      <td>{p.id}</td>
                      <td style={{ fontWeight: 600, color: 'var(--text-title)' }}>
                        {p.user.firstName} {p.user.lastName}
                      </td>
                      <td>{p.user.email}</td>
                      <td>{p.user.phone}</td>
                      <td>{p.dateOfBirth}</td>
                      <td>{p.gender}</td>
                      <td>
                        <span className="badge badge-info">{p.bloodGroup}</span>
                      </td>
                      {isAdmin && (
                        <td>
                          <button onClick={() => handleDeletePatient(p.id)} style={styles.deleteBtn}>
                            <Trash2 size={16} />
                          </button>
                        </td>
                      )}
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={isAdmin ? 8 : 7} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>
                      No patients registered in directory matching search criteria
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
  searchBar: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
    padding: '0.25rem 1rem',
    backgroundColor: '#ffffff',
    border: '1px solid var(--border-color)',
    borderRadius: 'var(--radius-sm)',
  },
  searchInput: {
    border: 'none',
    padding: '0.75rem 0',
  },
  deleteBtn: {
    background: 'none',
    border: 'none',
    color: 'var(--danger)',
    cursor: 'pointer',
    padding: '4px',
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
  sectionDivider: {
    fontSize: '0.9rem',
    fontWeight: 700,
    textTransform: 'uppercase',
    color: 'var(--primary)',
    borderBottom: '1px solid var(--border-color)',
    paddingBottom: '0.25rem',
    marginBottom: '1rem',
    marginTop: '1.5rem',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
  },
  row: {
    display: 'flex',
    gap: '1.5rem',
  },
};
