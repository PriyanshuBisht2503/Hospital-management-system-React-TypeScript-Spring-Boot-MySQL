import React from 'react';

export interface SelectOption {
  value: string | number;
  label: string;
}

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  options: SelectOption[];
  placeholder?: string;
}

export const Select: React.FC<SelectProps> = ({
  label,
  error,
  options,
  placeholder,
  style,
  id,
  value,
  ...props
}) => {
  const selectId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
      {label && (
        <label
          htmlFor={selectId}
          style={{ fontSize: '13px', fontWeight: 600, color: '#334155' }}
        >
          {label}
        </label>
      )}
      <select
        id={selectId}
        value={value}
        style={{
          padding: '10px 14px',
          borderRadius: '8px',
          border: error ? '1.5px solid #ef4444' : '1px solid #cbd5e1',
          fontSize: '14px',
          color: '#0f172a',
          outline: 'none',
          backgroundColor: '#ffffff',
          cursor: props.disabled ? 'not-allowed' : 'pointer',
          ...style,
        }}
        {...props}
      >
        {placeholder && (
          <option value="" disabled>
            {placeholder}
          </option>
        )}
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
      {error && (
        <span style={{ fontSize: '12px', color: '#ef4444' }}>
          {error}
        </span>
      )}
    </div>
  );
};

