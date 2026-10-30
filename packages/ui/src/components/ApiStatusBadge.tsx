import React, { useEffect, useState } from 'react';
import { Badge } from './Badge';

export interface ApiStatusBadgeProps {
  apiUrl?: string;
  onStatusChange?: (isOnline: boolean) => void;
}

export const ApiStatusBadge: React.FC<ApiStatusBadgeProps> = ({
  apiUrl = 'http://localhost:4000/api/health',
  onStatusChange,
}) => {
  const [status, setStatus] = useState<'checking' | 'online' | 'offline'>('checking');

  useEffect(() => {
    let isMounted = true;

    async function checkHealth() {
      try {
        const res = await fetch(apiUrl, { mode: 'cors' });
        if (res.ok) {
          const data = await res.json();
          if (data.status === 'UP' || data.status === 'OK' || data.status === 'ok') {
            if (isMounted) {
              setStatus('online');
              onStatusChange?.(true);
            }
            return;
          }
        }
        if (isMounted) {
          setStatus('offline');
          onStatusChange?.(false);
        }
      } catch (err) {
        if (isMounted) {
          setStatus('offline');
          onStatusChange?.(false);
        }
      }
    }

    checkHealth();
    const interval = setInterval(checkHealth, 5000);
    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, [apiUrl, onStatusChange]);

  if (status === 'checking') {
    return (
      <Badge variant="warning" id="api-status-badge">
        <span
          style={{
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            backgroundColor: '#f59e0b',
            display: 'inline-block',
          }}
        />
        Checking API...
      </Badge>
    );
  }

  if (status === 'online') {
    return (
      <Badge variant="success" id="api-status-badge">
        <span
          style={{
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            backgroundColor: '#22c55e',
            display: 'inline-block',
            boxShadow: '0 0 6px #22c55e',
          }}
        />
        API online
      </Badge>
    );
  }

  return (
    <Badge variant="danger" id="api-status-badge">
      <span
        style={{
          width: '8px',
          height: '8px',
          borderRadius: '50%',
          backgroundColor: '#ef4444',
          display: 'inline-block',
        }}
      />
      API offline
    </Badge>
  );
};
