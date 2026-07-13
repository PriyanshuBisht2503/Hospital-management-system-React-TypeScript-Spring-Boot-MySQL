import { useQuery } from '@tanstack/react-query';
import { dashboardApi } from '../../services/api/dashboardApi';
import { queryKeys } from '../../services/queryKeys';

export const useDashboardStatsQuery = (patientId: number) => {
  return useQuery({
    queryKey: queryKeys.dashboard.summary(),
    queryFn: () => dashboardApi.getDashboardStats(patientId),
  });
};
