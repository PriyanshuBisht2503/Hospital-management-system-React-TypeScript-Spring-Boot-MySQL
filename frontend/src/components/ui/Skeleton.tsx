import React from 'react';

interface SkeletonProps {
  className?: string;
  width?: string;
  height?: string;
  borderRadius?: string;
  style?: React.CSSProperties;
}

export const Skeleton: React.FC<SkeletonProps> = ({
  className = '',
  width = '100%',
  height = '1rem',
  borderRadius = '4px',
  style,
}) => {
  const defaultStyle: React.CSSProperties = {
    width,
    height,
    borderRadius,
    backgroundColor: '#e2e8f0',
    animation: 'pulse 1.5s cubic-bezier(0.4, 0, 0.6, 1) infinite',
    ...style,
  };

  return <div className={`skeleton ${className}`} style={defaultStyle} />;
};

// Add standard pulse keyframes in a style tag if not present in global CSS
if (typeof document !== 'undefined') {
  const styleId = 'skeleton-keyframes-style';
  if (!document.getElementById(styleId)) {
    const style = document.createElement('style');
    style.id = styleId;
    style.innerHTML = `
      @keyframes pulse {
        0%, 100% { opacity: 1; }
        50% { opacity: .5; }
      }
    `;
    document.head.appendChild(style);
  }
}
