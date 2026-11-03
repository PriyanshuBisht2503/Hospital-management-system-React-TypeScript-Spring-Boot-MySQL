import type { Role } from '../types/auth';

export interface StaffNavItem {
  label: string;
  href: string;
  roles: Role[];
}

/**
 * Phase 2 staff navigation entries. `roles` is the set of roles that see the entry.
 * The DOCTOR "My Schedule" entry's href is resolved against the logged-in doctor's
 * `doctorId` by `getPhase2Nav` (falling back to `/schedules` when none is linked).
 */
export const PHASE_2_NAV_ITEMS: StaffNavItem[] = [
  { label: 'Patients', href: '/patients', roles: ['ADMIN', 'RECEPTIONIST', 'DOCTOR'] },
  { label: 'Appointments', href: '/appointments', roles: ['ADMIN', 'RECEPTIONIST'] },
  { label: 'Schedules', href: '/schedules', roles: ['ADMIN', 'RECEPTIONIST'] },
  { label: 'Day Agenda', href: '/day-agenda', roles: ['ADMIN', 'RECEPTIONIST'] },
  { label: 'My Day', href: '/my-day', roles: ['DOCTOR'] },
  { label: 'My Schedule', href: '/schedules/me', roles: ['DOCTOR'] },
];

/** Returns the Phase 2 nav entries visible to a role, resolving the doctor's own schedule href. */
export function getPhase2Nav(role: Role, doctorId?: number | null): StaffNavItem[] {
  return PHASE_2_NAV_ITEMS
    .filter((item) => item.roles.includes(role))
    .map((item) =>
      item.href === '/schedules/me'
        ? { ...item, href: doctorId != null ? `/schedules/${doctorId}` : '/schedules' }
        : item,
    );
}
