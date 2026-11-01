import React from 'react';
import { Button } from './Button';

export interface PaginationProps {
  page: number; // 0-indexed (Spring Data standard)
  totalPages: number;
  totalElements?: number;
  onPageChange: (newPage: number) => void;
  style?: React.CSSProperties;
}

export const Pagination: React.FC<PaginationProps> = ({
  page,
  totalPages,
  totalElements,
  onPageChange,
  style,
}) => {
  if (totalPages <= 1 && (totalElements === undefined || totalElements === 0)) {
    return null;
  }

  const hasPrev = page > 0;
  const hasNext = page < totalPages - 1;

  // Render a window of pages around current page
  const pageNumbers: number[] = [];
  const startPage = Math.max(0, page - 2);
  const endPage = Math.min(totalPages - 1, page + 2);

  for (let i = startPage; i <= endPage; i++) {
    pageNumbers.push(i);
  }

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '12px 16px',
        backgroundColor: '#ffffff',
        borderTop: '1px solid #e2e8f0',
        borderRadius: '0 0 8px 8px',
        fontSize: '13px',
        color: '#64748b',
        flexWrap: 'wrap',
        gap: '12px',
        ...style,
      }}
    >
      <div>
        {totalElements !== undefined ? (
          <span>
            Total: <strong>{totalElements}</strong> items (Page <strong>{page + 1}</strong> of <strong>{Math.max(1, totalPages)}</strong>)
          </span>
        ) : (
          <span>
            Page <strong>{page + 1}</strong> of <strong>{Math.max(1, totalPages)}</strong>
          </span>
        )}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
        <Button
          variant="outline"
          size="sm"
          disabled={!hasPrev}
          onClick={() => onPageChange(page - 1)}
          style={{ padding: '4px 10px', fontSize: '12px' }}
        >
          Previous
        </Button>

        {startPage > 0 && (
          <>
            <button
              onClick={() => onPageChange(0)}
              style={{
                padding: '4px 8px',
                borderRadius: '6px',
                border: '1px solid #cbd5e1',
                background: '#ffffff',
                cursor: 'pointer',
                fontSize: '12px',
              }}
            >
              1
            </button>
            {startPage > 1 && <span>…</span>}
          </>
        )}

        {pageNumbers.map((p) => {
          const isActive = p === page;
          return (
            <button
              key={p}
              onClick={() => onPageChange(p)}
              style={{
                padding: '4px 10px',
                borderRadius: '6px',
                border: isActive ? '1px solid #0d9488' : '1px solid #cbd5e1',
                backgroundColor: isActive ? '#0d9488' : '#ffffff',
                color: isActive ? '#ffffff' : '#334155',
                fontWeight: isActive ? 600 : 400,
                cursor: 'pointer',
                fontSize: '12px',
              }}
            >
              {p + 1}
            </button>
          );
        })}

        {endPage < totalPages - 1 && (
          <>
            {endPage < totalPages - 2 && <span>…</span>}
            <button
              onClick={() => onPageChange(totalPages - 1)}
              style={{
                padding: '4px 8px',
                borderRadius: '6px',
                border: '1px solid #cbd5e1',
                background: '#ffffff',
                cursor: 'pointer',
                fontSize: '12px',
              }}
            >
              {totalPages}
            </button>
          </>
        )}

        <Button
          variant="outline"
          size="sm"
          disabled={!hasNext}
          onClick={() => onPageChange(page + 1)}
          style={{ padding: '4px 10px', fontSize: '12px' }}
        >
          Next
        </Button>
      </div>
    </div>
  );
};

