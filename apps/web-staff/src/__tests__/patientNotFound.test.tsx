import { describe, it, expect } from 'vitest';
import { renderToString } from 'react-dom/server';
import { PatientNotFound } from '../components/PatientNotFound';

describe('PatientNotFound renders', () => {
  it('shows "Patient not found"', () => {
    expect(renderToString(<PatientNotFound />)).toContain('Patient not found');
  });

  it('does not leak whether the id was nonexistent vs inaccessible', () => {
    const html = renderToString(<PatientNotFound />);
    // Single neutral message, identical for both 404 cases (backend cloaking).
    expect(html).toContain('does not exist or you do not have access');
  });
});
