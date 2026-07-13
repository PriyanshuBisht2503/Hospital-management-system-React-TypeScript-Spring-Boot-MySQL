import React, { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuth } from '../context/AuthContext';
import { Search, UserPlus, Trash2, ShieldAlert } from 'lucide-react';
import { patientRegistrationSchema } from '../features/patients/patientSchemas';
import type { PatientRegistrationInput } from '../features/patients/patientSchemas';
import { usePatientsQuery, useCreatePatientMutation, useDeletePatientMutation } from '../features/patients/usePatients';
import { Skeleton } from '../components/ui/Skeleton';
import { ErrorBoundaryWrapper } from '../components/ui/ErrorBoundaryWrapper';
import styles from './Patients.module.css';

const PatientDirectoryContent: React.FC = () => {
  const { user } = useAuth();
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [showAddForm, setShowAddForm] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  // Debounce search query input by 300ms to resolve server fetch race conditions
  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedQuery(searchQuery);
    }, 300);
    return () => clearTimeout(handler);
  }, [searchQuery]);

  const { data, isLoading, isError } = usePatientsQuery(debouncedQuery);
  const createMutation = useCreatePatientMutation();
  const deleteMutation = useDeletePatientMutation();

  const patients = data?.content || [];

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<PatientRegistrationInput>({
    resolver: zodResolver(patientRegistrationSchema),
    defaultValues: {
      gender: 'MALE',
      bloodGroup: 'O+',
    },
  });

  const onSubmit = async (formData: PatientRegistrationInput) => {
    setErrorMessage('');
    try {
      await createMutation.mutateAsync(formData);
      setShowAddForm(false);
      reset();
    } catch (err: any) {
      setErrorMessage(err.response?.data?.message || 'Failed to register patient profile');
    }
  };

  const handleDeletePatient = async (id: number) => {
    if (!window.confirm('Are you sure you want to delete this patient record?')) return;
    setErrorMessage('');
    try {
      await deleteMutation.mutateAsync(id);
    } catch (err: any) {
      setErrorMessage(err.response?.data?.message || 'Could not delete patient record. Ensure you have administrator rights.');
    }
  };

  const isAdmin = user?.roles.includes('ROLE_ADMIN');

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <div className={styles.headerText}>
          <h1 className={styles.title}>Patient Directory</h1>
          <p className={styles.subtitle}>Manage and register clinic outpatient profiles.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowAddForm(!showAddForm)}>
          <UserPlus size={18} />
          <span>{showAddForm ? 'View Directory' : 'Register New Patient'}</span>
        </button>
      </header>

      {(errorMessage || isError) && (
        <div className={`badge-danger ${styles.errorBox}`}>
          <ShieldAlert size={18} />
          <span>{errorMessage || 'Failed to sync with outpatient server'}</span>
        </div>
      )}

      {showAddForm ? (
        <div className={`card ${styles.formCard}`}>
          <h3 className={styles.formTitle}>Outpatient Registration Form</h3>
          <form onSubmit={handleSubmit(onSubmit)} className={styles.form}>
            <h4 className={styles.sectionDivider}>Account Credentials</h4>
            <div className={styles.row}>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Email Address</label>
                <input type="email" className="form-input" {...register('user.email')} />
                {errors.user?.email && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.user.email.message}</span>}
              </div>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Password</label>
                <input type="password" className="form-input" {...register('user.password')} />
                {errors.user?.password && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.user.password.message}</span>}
              </div>
            </div>

            <h4 className={styles.sectionDivider}>Personal Profile Details</h4>
            <div className={styles.row}>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">First Name</label>
                <input type="text" className="form-input" {...register('user.firstName')} />
                {errors.user?.firstName && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.user.firstName.message}</span>}
              </div>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Last Name</label>
                <input type="text" className="form-input" {...register('user.lastName')} />
                {errors.user?.lastName && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.user.lastName.message}</span>}
              </div>
            </div>

            <div className={styles.row}>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Phone Number</label>
                <input type="tel" className="form-input" {...register('user.phone')} />
                {errors.user?.phone && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.user.phone.message}</span>}
              </div>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Date of Birth</label>
                <input type="date" className="form-input" {...register('dateOfBirth')} />
                {errors.dateOfBirth && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.dateOfBirth.message}</span>}
              </div>
            </div>

            <div className={styles.row}>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Gender</label>
                <select className="form-input" {...register('gender')}>
                  <option value="MALE">Male</option>
                  <option value="FEMALE">Female</option>
                  <option value="OTHER">Other</option>
                </select>
                {errors.gender && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.gender.message}</span>}
              </div>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Blood Group</label>
                <select className="form-input" {...register('bloodGroup')}>
                  <option value="A+">A+</option>
                  <option value="A-">A-</option>
                  <option value="B+">B+</option>
                  <option value="B-">B-</option>
                  <option value="AB+">AB+</option>
                  <option value="AB-">AB-</option>
                  <option value="O+">O+</option>
                  <option value="O-">O-</option>
                </select>
                {errors.bloodGroup && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.bloodGroup.message}</span>}
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Address</label>
              <textarea className="form-input" rows={2} {...register('address')} />
              {errors.address && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.address.message}</span>}
            </div>

            <h4 className={styles.sectionDivider}>Emergency Contacts</h4>
            <div className={styles.row}>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Contact Name</label>
                <input type="text" className="form-input" {...register('emergencyContactName')} />
                {errors.emergencyContactName && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.emergencyContactName.message}</span>}
              </div>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Contact Phone</label>
                <input type="tel" className="form-input" {...register('emergencyContactPhone')} />
                {errors.emergencyContactPhone && <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>{errors.emergencyContactPhone.message}</span>}
              </div>
            </div>

            <button type="submit" className="btn btn-primary" style={{ marginTop: '1rem' }} disabled={isSubmitting || createMutation.isPending}>
              {isSubmitting || createMutation.isPending ? 'Submitting Registration...' : 'Register Profile'}
            </button>
          </form>
        </div>
      ) : (
        <div className={styles.listContainer}>
          <div className={styles.searchBar}>
            <Search size={20} color="var(--text-muted)" />
            <input
              type="text"
              className={styles.searchInput}
              placeholder="Search patients by name or email..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
            />
          </div>

          {isLoading ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '1rem' }}>
              <Skeleton height="40px" />
              <Skeleton height="50px" />
              <Skeleton height="50px" />
              <Skeleton height="50px" />
            </div>
          ) : (
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
                            <button onClick={() => handleDeletePatient(p.id)} className={styles.deleteBtn} disabled={deleteMutation.isPending}>
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
          )}
        </div>
      )}
    </div>
  );
};

export const Patients: React.FC = () => {
  return (
    <ErrorBoundaryWrapper name="PatientsDirectory">
      <PatientDirectoryContent />
    </ErrorBoundaryWrapper>
  );
};
export default Patients;
