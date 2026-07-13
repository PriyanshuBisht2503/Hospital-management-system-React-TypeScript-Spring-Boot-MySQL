import { useQuery } from '@tanstack/react-query';
import { doctorApi } from '../../services/api/doctorApi';
import { queryKeys } from '../../services/queryKeys';

export const useDoctorsQuery = () => {
  return useQuery({
    queryKey: queryKeys.doctors.lists(),
    queryFn: () => doctorApi.getDoctors(),
  });
};
