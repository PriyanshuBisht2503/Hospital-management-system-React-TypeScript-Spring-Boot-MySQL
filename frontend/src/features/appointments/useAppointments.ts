import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { appointmentApi } from '../../services/api/appointmentApi';
import { queryKeys } from '../../services/queryKeys';
import type { Appointment, PagedResponse } from '../../types';

export const usePatientAppointmentsQuery = (patientId: number, enabled: boolean = true) => {
  return useQuery({
    queryKey: queryKeys.appointments.list({ patientId }),
    queryFn: () => appointmentApi.getPatientAppointments(patientId),
    enabled,
  });
};

export const useDoctorAppointmentsQuery = (doctorId: number, enabled: boolean = true) => {
  return useQuery({
    queryKey: queryKeys.appointments.list({ doctorId }),
    queryFn: () => appointmentApi.getDoctorAppointments(doctorId),
    enabled,
  });
};

export const useCreateAppointmentMutation = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: {
      patientId: number;
      doctorId: number;
      appointmentDate: string;
      timeSlot: string;
    }) => appointmentApi.createAppointment(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.appointments.lists(),
      });
    },
  });
};

export const useCancelAppointmentMutation = (filter: { patientId?: number; doctorId?: number }) => {
  const queryClient = useQueryClient();
  const listQueryKey = queryKeys.appointments.list(filter);

  return useMutation({
    mutationFn: (id: number) => appointmentApi.cancelAppointment(id),
    // Optimistic Update & Rollback Integration
    onMutate: async (cancelledId: number) => {
      // Cancel outgoing fetches on this key
      await queryClient.cancelQueries({ queryKey: listQueryKey });

      // Snapshot active state in cache
      const previousData = queryClient.getQueryData<PagedResponse<Appointment>>(listQueryKey);

      // Optimistically update appointment item status inline
      queryClient.setQueryData<PagedResponse<Appointment>>(listQueryKey, (old) => {
        if (!old || !old.content) return old;
        return {
          ...old,
          content: old.content.map((item) =>
            item.id === cancelledId ? { ...item, status: 'CANCELLED' } : item
          ),
        };
      });

      // Expose fallback snapshotted state to context
      return { previousData };
    },
    onError: (_err, _cancelledId, context) => {
      // Rollback to cached state on fetch failure
      if (context?.previousData) {
        queryClient.setQueryData(listQueryKey, context.previousData);
      }
    },
    onSettled: () => {
      // Sync cache background fetch with database truth
      queryClient.invalidateQueries({ queryKey: listQueryKey });
    },
  });
};
