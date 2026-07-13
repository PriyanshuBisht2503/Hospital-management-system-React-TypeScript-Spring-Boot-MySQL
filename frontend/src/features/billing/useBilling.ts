import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { billingApi } from '../../services/api/billingApi';
import { queryKeys } from '../../services/queryKeys';

export const useInvoicesQuery = (patientId: number) => {
  return useQuery({
    queryKey: queryKeys.billing.list({ patientId }),
    queryFn: () => billingApi.getPatientInvoices(patientId),
  });
};

export const useGenerateInvoiceMutation = (patientId: number) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: { appointmentId: number; fee: number }) =>
      billingApi.generateInvoice(payload.appointmentId, payload.fee),
    onSuccess: () => {
      // Invalidate target billing list to force background refresh
      queryClient.invalidateQueries({
        queryKey: queryKeys.billing.list({ patientId }),
      });
    },
  });
};

export const useRecordPaymentMutation = (patientId: number) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: {
      invoiceId: number;
      amount: number;
      paymentMethod: string;
      transactionId: string;
    }) => billingApi.recordPayment(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.billing.list({ patientId }),
      });
    },
  });
};
