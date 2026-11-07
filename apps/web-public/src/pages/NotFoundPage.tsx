import React from 'react';
import { Link } from 'react-router-dom';
import { useDocumentTitle } from '../hooks/useDocumentTitle';

export const NotFoundPage: React.FC = () => {
  useDocumentTitle('Page not found');

  return (
    <div style={{ textAlign: 'center', padding: '64px 0' }}>
      <h1 style={{ margin: '0 0 10px', fontSize: '28px', color: '#0f172a' }}>Page not found</h1>
      <p style={{ margin: '0 0 20px', color: '#64748b' }}>The page you were looking for does not exist.</p>
      <Link to="/" style={{ color: '#0d9488', fontWeight: 600 }}>Back to home</Link>
    </div>
  );
};
