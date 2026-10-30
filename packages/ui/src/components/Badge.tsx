import React from 'react';

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: 'success' | 'danger' | 'warning' | 'info' | 'neutral';
  children: React.ReactNode;
}

export const Badge: React.FC<BadgeProps> = ({
  variant = 'neutral',
  children,
  style,
  ...props
}) => {
  const variantStyles: Record<string, React.CSSProperties> = {
    success: { backgroundColor: '#dcfce7', color: '#15803d', border: '1px solid #bbf7d0' },
    danger: { backgroundColor: '#fee2e2', color: '#b91c1c', border: '1px solid #fecaca' },
    warning: { backgroundColor: '#fef3c7', color: '#b45309', border: '1px solid #fde68a' },
    info: { backgroundColor: '#e0f2fe', color: '#0369a1', border: '1px solid #bae6fd' },
    neutral: { backgroundColor: '#f1f5f9', color: '#475569', border: '1px solid #e2e8f0' },
  };

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '6px',
        padding: '3px 10px',
        borderRadius: '9999px',
        fontSize: '12px',
        fontWeight: 600,
        ...variantStyles[variant],
        ...style,
      }}
      {...props}
    >
      {children}
    </span>
  );
};
