import React from 'react';
import { Badge } from '@hms/ui';

export interface UserStatusBadgeProps {
  enabled: boolean;
}

/** Renders "Active" or "Disabled" for an admin user-list row (mirrors `UserDto.enabled`). */
export const UserStatusBadge: React.FC<UserStatusBadgeProps> = ({ enabled }) => (
  <Badge variant={enabled ? 'success' : 'neutral'}>
    {enabled ? 'Active' : 'Disabled'}
  </Badge>
);
