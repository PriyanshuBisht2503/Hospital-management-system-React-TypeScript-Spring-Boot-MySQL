import { describe, it, expect } from 'vitest';
import { renderToString } from 'react-dom/server';
import { StatusBadge } from '../components/StatusBadge';

describe('StatusBadge renders', () => {
  it('renders the status label text (not colour only)', () => {
    const html = renderToString(<StatusBadge status="CONFIRMED" />);
    expect(html).toContain('Confirmed');
  });

  it('renders the needsRescheduling badge when flagged', () => {
    const html = renderToString(<StatusBadge status="CONFIRMED" needsRescheduling />);
    expect(html).toContain('Needs rescheduling');
  });

  it('does not render the needsRescheduling badge when not flagged', () => {
    const html = renderToString(<StatusBadge status="CONFIRMED" needsRescheduling={false} />);
    expect(html).not.toContain('Needs rescheduling');
  });
});
