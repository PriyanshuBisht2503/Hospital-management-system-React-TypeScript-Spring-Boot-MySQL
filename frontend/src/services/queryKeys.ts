/**
 * Centralized Query Key Registry.
 * Enforces namespaced and structured query keys to handle cache invalidation consistently.
 */

export const queryKeys = {
  auth: {
    all: ['auth'] as const,
    profile: () => [...queryKeys.auth.all, 'profile'] as const,
  },
  patients: {
    all: ['patients'] as const,
    lists: () => [...queryKeys.patients.all, 'list'] as const,
    list: (query: string) => [...queryKeys.patients.lists(), { query }] as const,
    details: () => [...queryKeys.patients.all, 'detail'] as const,
    detail: (id: string | number) => [...queryKeys.patients.details(), id] as const,
  },
  doctors: {
    all: ['doctors'] as const,
    lists: () => [...queryKeys.doctors.all, 'list'] as const,
    list: (query: string) => [...queryKeys.doctors.lists(), { query }] as const,
    details: () => [...queryKeys.doctors.all, 'detail'] as const,
    detail: (id: string | number) => [...queryKeys.doctors.details(), id] as const,
  },
  appointments: {
    all: ['appointments'] as const,
    lists: () => [...queryKeys.appointments.all, 'list'] as const,
    list: (filter: Record<string, any>) => [...queryKeys.appointments.lists(), filter] as const,
    details: () => [...queryKeys.appointments.all, 'detail'] as const,
    detail: (id: string | number) => [...queryKeys.appointments.details(), id] as const,
  },
  billing: {
    all: ['billing'] as const,
    lists: () => [...queryKeys.billing.all, 'list'] as const,
    list: (filter: Record<string, any>) => [...queryKeys.billing.lists(), filter] as const,
    details: () => [...queryKeys.billing.all, 'detail'] as const,
    detail: (id: string | number) => [...queryKeys.billing.details(), id] as const,
  },
  dashboard: {
    all: ['dashboard'] as const,
    summary: () => [...queryKeys.dashboard.all, 'summary'] as const,
  },
};
