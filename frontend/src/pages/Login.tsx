import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { Activity, ShieldAlert } from 'lucide-react';

export const Login: React.FC = () => {
  const { login, register } = useAuth();
  const [isRegister, setIsRegister] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [phone, setPhone] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      if (isRegister) {
        await register({ email, password, firstName, lastName, phone });
        setSuccess('Registration successful! You can now log in.');
        setIsRegister(false);
      } else {
        await login(email, password);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Authentication failed. Please verify credentials.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      <div className="card" style={styles.card}>
        <div style={styles.logoContainer}>
          <Activity size={36} color="var(--primary)" />
          <h2 style={styles.logoText}>HMS Redesign</h2>
        </div>

        <h3 style={styles.subtitle}>
          {isRegister ? 'Create Patient Account' : 'Welcome back'}
        </h3>
        <p style={styles.desc}>
          {isRegister 
            ? 'Sign up to manage your doctor appointments and medical bills' 
            : 'Sign in to access your dashboard'}
        </p>

        {error && (
          <div style={styles.errorBanner}>
            <ShieldAlert size={20} color="var(--danger)" />
            <span>{error}</span>
          </div>
        )}

        {success && (
          <div style={styles.successBanner}>
            <span>{success}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} style={styles.form}>
          {isRegister && (
            <>
              <div style={styles.row}>
                <div className="form-group" style={{ flex: 1 }}>
                  <label className="form-label">First Name</label>
                  <input
                    type="text"
                    className="form-input"
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                    required
                  />
                </div>
                <div className="form-group" style={{ flex: 1 }}>
                  <label className="form-label">Last Name</label>
                  <input
                    type="text"
                    className="form-input"
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    required
                  />
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Phone Number</label>
                <input
                  type="tel"
                  className="form-input"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  required
                />
              </div>
            </>
          )}

          <div className="form-group">
            <label className="form-label">Email Address</label>
            <input
              type="email"
              className="form-input"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label">Password</label>
            <input
              type="password"
              className="form-input"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <button type="submit" className="btn btn-primary" style={styles.submitBtn} disabled={loading}>
            {loading ? 'Processing...' : isRegister ? 'Register Account' : 'Sign In'}
          </button>
        </form>

        <div style={styles.toggleContainer}>
          <button 
            type="button" 
            style={styles.toggleLink} 
            onClick={() => {
              setIsRegister(!isRegister);
              setError('');
              setSuccess('');
            }}
          >
            {isRegister ? 'Already have an account? Sign In' : "Don't have an account? Sign Up"}
          </button>
        </div>
      </div>
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '100vh',
    backgroundColor: '#f1f5f9',
    padding: '1.5rem',
  },
  card: {
    width: '100%',
    maxWidth: '450px',
    padding: '2.5rem',
    borderRadius: '12px',
    boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
  },
  logoContainer: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '0.75rem',
    marginBottom: '1.5rem',
  },
  logoText: {
    fontSize: '1.5rem',
    fontWeight: 800,
    color: 'var(--text-title)',
  },
  subtitle: {
    textAlign: 'center',
    fontSize: '1.25rem',
    fontWeight: 700,
    marginBottom: '0.25rem',
  },
  desc: {
    textAlign: 'center',
    fontSize: '0.875rem',
    color: 'var(--text-muted)',
    marginBottom: '2rem',
  },
  errorBanner: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
    padding: '0.75rem 1rem',
    borderRadius: '6px',
    backgroundColor: 'var(--danger-light)',
    color: 'var(--danger)',
    fontSize: '0.875rem',
    fontWeight: 600,
    marginBottom: '1.5rem',
  },
  successBanner: {
    padding: '0.75rem 1rem',
    borderRadius: '6px',
    backgroundColor: 'var(--success-light)',
    color: 'var(--success)',
    fontSize: '0.875rem',
    fontWeight: 600,
    marginBottom: '1.5rem',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
  },
  row: {
    display: 'flex',
    gap: '1rem',
  },
  submitBtn: {
    width: '100%',
    padding: '0.85rem',
    marginTop: '1rem',
  },
  toggleContainer: {
    textAlign: 'center',
    marginTop: '1.5rem',
  },
  toggleLink: {
    background: 'none',
    border: 'none',
    color: 'var(--primary)',
    fontWeight: 600,
    cursor: 'pointer',
    fontSize: '0.875rem',
  },
};
