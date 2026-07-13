import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { patientApi } from '../../services/api/patientApi';
import { queryKeys } from '../../services/queryKeys';
import type { PatientRegistrationInput } from './patientSchemas';

export const usePatientsQuery = (searchQuery: string) => {
  return useQuery({
    queryKey: queryKeys.patients.list(searchQuery),
    queryFn: () => patientApi.getPatients(searchQuery),
    placeholderData: (previousData) => previousData, // Maintain previous screen view while typing
  });
};

export const useCreatePatientMutation = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: PatientRegistrationInput) => patientApi.createPatient(payload),
    onSuccess: () => {
      // Invalidate all patients directory caches to trigger background refetch
      queryClient.invalidateQueries({
        queryKey: queryKeys.patients.lists(),
      });
    },
  });
};

export const useDeletePatientMutation = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => patientApi.deletePatient(id),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.patients.lists(),
      });
    },
  });
};
