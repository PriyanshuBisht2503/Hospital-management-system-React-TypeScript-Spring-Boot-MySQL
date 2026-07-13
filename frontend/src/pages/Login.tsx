import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuth } from '../context/AuthContext';
import { Activity, ShieldAlert } from 'lucide-react';
import { 
  loginSchema, 
  registerSchema, 
} from '../features/auth/authSchemas';
import type {
  LoginInput, 
  RegisterInput 
} from '../features/auth/authSchemas';
import styles from './Login.module.css';

export const Login: React.FC = () => {
  const { login, register } = useAuth();
  const [isRegister, setIsRegister] = useState(false);
  const [authError, setAuthError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [loading, setLoading] = useState(false);

  const loginForm = useForm<LoginInput>({
    resolver: zodResolver(loginSchema),
  });

  const registerForm = useForm<RegisterInput>({
    resolver: zodResolver(registerSchema),
  });

  const handleLoginSubmit = async (data: LoginInput) => {
    setAuthError('');
    setSuccessMessage('');
    setLoading(true);
    try {
      await login(data.email, data.password);
    } catch (err: any) {
      setAuthError(err.response?.data?.message || 'Authentication failed. Please verify credentials.');
      setLoading(false);
    }
  };

  const handleRegisterSubmit = async (data: RegisterInput) => {
    setAuthError('');
    setSuccessMessage('');
    setLoading(true);
    try {
      await register({
        email: data.email,
        password: data.password,
        firstName: data.firstName,
        lastName: data.lastName,
        phone: data.phone,
      });
      setSuccessMessage('Registration successful! You can now log in.');
      setIsRegister(false);
      registerForm.reset();
    } catch (err: any) {
      setAuthError(err.response?.data?.message || 'Registration failed. Verify email is unique.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={`card ${styles.card}`}>
        <div className={styles.logoContainer}>
          <Activity size={36} color="var(--primary)" />
          <h2 className={styles.logoText}>HMS Redesign</h2>
        </div>

        <h3 className={styles.subtitle}>
          {isRegister ? 'Create Patient Account' : 'Welcome back'}
        </h3>
        <p className={styles.desc}>
          {isRegister 
            ? 'Sign up to manage your doctor appointments and medical bills' 
            : 'Sign in to access your dashboard'}
        </p>

        {authError && (
          <div className={styles.errorBanner}>
            <ShieldAlert size={20} color="var(--danger)" />
            <span>{authError}</span>
          </div>
        )}

        {successMessage && (
          <div className={styles.successBanner}>
            <span>{successMessage}</span>
          </div>
        )}

        {isRegister ? (
          <form onSubmit={registerForm.handleSubmit(handleRegisterSubmit)} className={styles.form}>
            <div className={styles.row}>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">First Name</label>
                <input
                  type="text"
                  className="form-input"
                  {...registerForm.register('firstName')}
                />
                {registerForm.formState.errors.firstName && (
                  <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                    {registerForm.formState.errors.firstName.message}
                  </span>
                )}
              </div>
              <div className="form-group" style={{ flex: 1 }}>
                <label className="form-label">Last Name</label>
                <input
                  type="text"
                  className="form-input"
                  {...registerForm.register('lastName')}
                />
                {registerForm.formState.errors.lastName && (
                  <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                    {registerForm.formState.errors.lastName.message}
                  </span>
                )}
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Phone Number</label>
              <input
                type="tel"
                className="form-input"
                {...registerForm.register('phone')}
              />
              {registerForm.formState.errors.phone && (
                <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                  {registerForm.formState.errors.phone.message}
                </span>
              )}
            </div>

            <div className="form-group">
              <label className="form-label">Email Address</label>
              <input
                type="email"
                className="form-input"
                {...registerForm.register('email')}
              />
              {registerForm.formState.errors.email && (
                <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                  {registerForm.formState.errors.email.message}
                </span>
              )}
            </div>

            <div className="form-group">
              <label className="form-label">Password</label>
              <input
                type="password"
                className="form-input"
                {...registerForm.register('password')}
              />
              {registerForm.formState.errors.password && (
                <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                  {registerForm.formState.errors.password.message}
                </span>
              )}
            </div>

            <button type="submit" className={`btn btn-primary ${styles.submitBtn}`} disabled={loading}>
              {loading ? 'Processing...' : 'Register Account'}
            </button>
          </form>
        ) : (
          <form onSubmit={loginForm.handleSubmit(handleLoginSubmit)} className={styles.form}>
            <div className="form-group">
              <label className="form-label">Email Address</label>
              <input
                type="email"
                className="form-input"
                {...loginForm.register('email')}
              />
              {loginForm.formState.errors.email && (
                <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                  {loginForm.formState.errors.email.message}
                </span>
              )}
            </div>

            <div className="form-group">
              <label className="form-label">Password</label>
              <input
                type="password"
                className="form-input"
                {...loginForm.register('password')}
              />
              {loginForm.formState.errors.password && (
                <span style={{ color: 'var(--danger)', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                  {loginForm.formState.errors.password.message}
                </span>
              )}
            </div>

            <button type="submit" className={`btn btn-primary ${styles.submitBtn}`} disabled={loading}>
              {loading ? 'Processing...' : 'Sign In'}
            </button>
          </form>
        )}

        <div className={styles.toggleContainer}>
          <button 
            type="button" 
            className={styles.toggleLink} 
            onClick={() => {
              setIsRegister(!isRegister);
              setAuthError('');
              setSuccessMessage('');
              loginForm.reset();
              registerForm.reset();
            }}
          >
            {isRegister ? 'Already have an account? Sign In' : "Don't have an account? Sign Up"}
          </button>
        </div>
      </div>
    </div>
  );
};
export default Login;
