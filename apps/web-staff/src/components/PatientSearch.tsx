import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Input } from '@hms/ui';
import { listPatients } from '../api/patients';
import type { Patient } from '../types/patient';

export interface PatientSearchProps {
  onSelect: (patient: Patient) => void;
  placeholder?: string;
}

/** Debounced-ish patient typeahead: search patients and select one. */
export const PatientSearch: React.FC<PatientSearchProps> = ({ onSelect, placeholder = 'Search patients...' }) => {
  const [term, setTerm] = useState('');
  const [open, setOpen] = useState(false);

  const { data } = useQuery({
    queryKey: ['patient-search', term],
    queryFn: () => listPatients({ search: term, size: 10 }),
    enabled: term.trim().length > 0,
  });

  return (
    <div style={{ position: 'relative' }}>
      <Input
        value={term}
        placeholder={placeholder}
        onChange={(e) => { setTerm(e.target.value); setOpen(true); }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
      />
      {open && term.trim().length > 0 && (
        <ul style={{ position: 'absolute', zIndex: 10, width: '100%', margin: '4px 0 0', padding: 0, listStyle: 'none', backgroundColor: '#fff', border: '1px solid #e2e8f0', borderRadius: '8px', maxHeight: '200px', overflowY: 'auto' }}>
          {data && data.content.length === 0 && <li style={{ padding: '8px 12px', color: '#64748b' }}>No patients found</li>}
          {data?.content.map((p) => (
            <li key={p.id} style={{ padding: '8px 12px', cursor: 'pointer', fontSize: '14px' }} onMouseDown={() => { onSelect(p); setTerm(''); setOpen(false); }}>
              {p.fullName}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};
