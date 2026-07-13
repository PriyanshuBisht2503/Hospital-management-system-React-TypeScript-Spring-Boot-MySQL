import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error: any) => {
        // Never retry client status codes
        const status = error?.response?.status;
        if (status && [400, 401, 403, 404].includes(status)) {
          return false;
        }
        // Limit transient retries to 2 attempts
        if (failureCount >= 2) {
          return false;
        }
        return true;
      },
      staleTime: 300000, // 5 minutes standard stale time
      gcTime: 600000,    // 10 minutes cache garbage collection time
      refetchOnReconnect: true,
      refetchOnWindowFocus: false, // Avoid refetching when switching back to tab
      networkMode: 'online',
    },
    mutations: {
      networkMode: 'online',
    },
  },
});
