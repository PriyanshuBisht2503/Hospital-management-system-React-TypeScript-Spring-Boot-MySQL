import React from 'react';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

export const Input: React.FC<InputProps> = ({
  label,
  error,
  style,
  id,
  ...props
}) => {
  const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
      {label && (
        <label
          htmlFor={inputId}
          style={{ fontSize: '13px', fontWeight: 600, color: '#334155' }}
        >
          {label}
        </label>
      )}
      <input
        id={inputId}
        style={{
          padding: '10px 14px',
          borderRadius: '8px',
          border: error ? '1.5px solid #ef4444' : '1px solid #cbd5e1',
          fontSize: '14px',
          color: '#0f172a',
          outline: 'none',
          backgroundColor: '#ffffff',
          ...style,
        }}
        {...props}
      />
      {error && (
        <span style={{ fontSize: '12px', color: '#ef4444' }}>
          {error}
        </span>
      )}
    </div>
  );
};
