import React, { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { ShieldAlert } from 'lucide-react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
  name?: string;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundaryWrapper extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error(`Uncaught error in boundary [${this.props.name || 'Feature'}]:`, error, errorInfo);
  }

  private handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  public render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div style={styles.container}>
          <ShieldAlert size={36} color="var(--danger)" />
          <div style={styles.textContainer}>
            <h4 style={styles.title}>Panel Rendering Failure</h4>
            <p style={styles.text}>
              An error occurred while loading this section of the screen.
            </p>
          </div>
          <button onClick={this.handleReset} className="btn btn-outline" style={styles.button}>
            Retry View
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    padding: '1.5rem',
    border: '1px solid var(--border-color)',
    borderRadius: 'var(--radius-md)',
    backgroundColor: '#fff',
    display: 'flex',
    alignItems: 'center',
    gap: '1rem',
    flexWrap: 'wrap',
    boxShadow: 'var(--shadow-sm)',
  },
  textContainer: {
    flex: 1,
    textAlign: 'left',
  },
  title: {
    fontSize: '1rem',
    fontWeight: 700,
    margin: 0,
    color: 'var(--text-title)',
  },
  text: {
    fontSize: '0.85rem',
    color: 'var(--text-muted)',
    margin: '0.25rem 0 0 0',
  },
  button: {
    padding: '0.5rem 1rem',
    fontSize: '0.85rem',
  },
};
