import React, { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { ShieldAlert } from 'lucide-react';

interface Props {
  children?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Uncaught React render error:', error, errorInfo);
  }

  public render() {
    if (this.state.hasError) {
      return (
        <div style={styles.container}>
          <div className="card" style={styles.card}>
            <ShieldAlert size={48} color="var(--danger)" style={{ marginBottom: '1rem' }} />
            <h2 style={styles.title}>Application Crash Intercepted</h2>
            <p style={styles.text}>
              An unexpected layout rendering exception occurred in this panel.
            </p>
            {this.state.error && (
              <pre style={styles.errorStack}>{this.state.error.toString()}</pre>
            )}
            <button
              onClick={() => window.location.reload()}
              className="btn btn-primary"
              style={{ marginTop: '1.5rem' }}
            >
              Reload Application
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '100vh',
    backgroundColor: '#f8fafc',
    padding: '2rem',
  },
  card: {
    width: '100%',
    maxWidth: '500px',
    textAlign: 'center',
    padding: '2.5rem',
  },
  title: {
    fontSize: '1.25rem',
    fontWeight: 700,
    marginBottom: '0.5rem',
  },
  text: {
    fontSize: '0.9rem',
    color: 'var(--text-muted)',
    marginBottom: '1rem',
  },
  errorStack: {
    textAlign: 'left',
    padding: '1rem',
    backgroundColor: '#f1f5f9',
    borderRadius: '6px',
    fontSize: '0.8rem',
    color: 'var(--danger)',
    overflowX: 'auto',
    whiteSpace: 'pre-wrap',
  },
};
