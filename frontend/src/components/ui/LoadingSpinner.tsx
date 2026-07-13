import React from 'react';

interface LoadingSpinnerProps {
  size?: string;
  color?: string;
  text?: string;
}

export const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  size = '32px',
  color = 'var(--primary)',
  text,
}) => {
  return (
    <div style={styles.container}>
      <div
        style={{
          ...styles.spinner,
          width: size,
          height: size,
          borderTopColor: color,
        }}
      />
      {text && <p style={styles.text}>{text}</p>}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '0.75rem',
    padding: '2rem',
  },
  spinner: {
    border: '3px solid var(--border-color)',
    borderRadius: '50%',
    animation: 'spin 1s linear infinite',
  },
  text: {
    fontSize: '0.9rem',
    color: 'var(--text-muted)',
    fontWeight: 600,
    margin: 0,
  },
};

// Add standard spin animation keyframes in a style tag if not present
if (typeof document !== 'undefined') {
  const styleId = 'spinner-keyframes-style';
  if (!document.getElementById(styleId)) {
    const style = document.createElement('style');
    style.id = styleId;
    style.innerHTML = `
      @keyframes spin {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
      }
    `;
    document.head.appendChild(style);
  }
}
