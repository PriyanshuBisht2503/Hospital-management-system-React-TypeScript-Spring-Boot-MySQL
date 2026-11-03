import { describe, it, expect } from 'vitest';
import { renderToString } from 'react-dom/server';
import { UserStatusBadge } from '../components/UserStatusBadge';

describe('UserStatusBadge renders', () => {
  it('shows Active for an enabled user', () => {
    expect(renderToString(<UserStatusBadge enabled />)).toContain('Active');
  });

  it('shows Disabled for a disabled user', () => {
    expect(renderToString(<UserStatusBadge enabled={false} />)).toContain('Disabled');
  });
});
