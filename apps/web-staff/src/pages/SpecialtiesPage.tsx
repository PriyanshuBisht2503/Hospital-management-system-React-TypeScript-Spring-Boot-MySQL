import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { apiRequest } from '../api/client';
import { Specialty, PageResponse } from '../types/masterData';
import { ApiError } from '../types/auth';
import {
  Button,
  Card,
  Input,
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
  Badge,
  Modal,
  Pagination,
  ConfirmDialog,
  useToast,
} from '@hms/ui';
import { Search, Plus, Edit2, Ban } from 'lucide-react';

const specialtySchema = z.object({
  code: z.string().min(2, 'Code must be at least 2 characters').max(20),
  name: z.string().min(2, 'Specialty name is required'),
  description: z.string().optional(),
});

type SpecialtyFormData = z.infer<typeof specialtySchema>;

export const SpecialtiesPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingSpecialty, setEditingSpecialty] = useState<Specialty | null>(null);

  const [deactivatingSpecialty, setDeactivatingSpecialty] = useState<Specialty | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SpecialtyFormData>();

  // Fetch paginated specialties
  const { data, isLoading, isError, error, refetch } = useQuery<PageResponse<Specialty>>({
    queryKey: ['specialties', page, search],
    queryFn: () =>
      apiRequest<PageResponse<Specialty>>(
        `/api/specialties?page=${page}&size=10&search=${encodeURIComponent(search)}&activeOnly=false`
      ),
  });

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (newSpecialty: SpecialtyFormData) =>
      apiRequest<Specialty>('/api/specialties', {
        method: 'POST',
        body: JSON.stringify(newSpecialty),
      }),
    onSuccess: () => {
      toast.success('Specialty created successfully');
      queryClient.invalidateQueries({ queryKey: ['specialties'] });
      closeModal();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.fieldErrors) {
        Object.entries(err.fieldErrors).forEach(([field, msg]) => {
          setError(field as keyof SpecialtyFormData, { message: msg });
        });
      } else if (err instanceof ApiError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to create specialty');
      }
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<SpecialtyFormData> }) =>
      apiRequest<Specialty>(`/api/specialties/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),
    onSuccess: () => {
      toast.success('Specialty updated successfully');
      queryClient.invalidateQueries({ queryKey: ['specialties'] });
      closeModal();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.fieldErrors) {
        Object.entries(err.fieldErrors).forEach(([field, msg]) => {
          setError(field as keyof SpecialtyFormData, { message: msg });
        });
      } else if (err instanceof ApiError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to update specialty');
      }
    },
  });

  // Soft-delete / Deactivate mutation
  const deactivateMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/api/specialties/${id}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      toast.success('Specialty deactivated successfully');
      queryClient.invalidateQueries({ queryKey: ['specialties'] });
      setDeactivatingSpecialty(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        toast.error(err.message);
      } else {
        toast.error('Failed to deactivate specialty');
      }
      setDeactivatingSpecialty(null);
    },
  });

  const openCreateModal = () => {
    setEditingSpecialty(null);
    setActionError(null);
    reset({
      code: '',
      name: '',
      description: '',
    });
    setIsModalOpen(true);
  };

  const openEditModal = (specialty: Specialty) => {
    setEditingSpecialty(specialty);
    setActionError(null);
    reset({
      code: specialty.code,
      name: specialty.name,
      description: specialty.description || '',
    });
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditingSpecialty(null);
    setActionError(null);
  };

  const onFormSubmit = async (formData: SpecialtyFormData) => {
    setActionError(null);
    const validation = specialtySchema.safeParse(formData);
    if (!validation.success) {
      for (const issue of validation.error.issues) {
        setError(issue.path[0] as keyof SpecialtyFormData, { message: issue.message });
      }
      return;
    }

    if (editingSpecialty) {
      await updateMutation.mutateAsync({ id: editingSpecialty.id, data: formData });
    } else {
      await createMutation.mutateAsync(formData);
    }
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setSearch(searchInput);
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>
            Medical Specialties
          </h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>
            Configure clinical practice specialties and doctor qualifications
          </p>
        </div>
        <Button variant="primary" size="md" onClick={openCreateModal}>
          <Plus size={16} />
          Add Specialty
        </Button>
      </div>

      {/* Search Bar */}
      <Card style={{ marginBottom: '20px', padding: '16px' }}>
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
          <div style={{ flex: 1 }}>
            <Input
              type="text"
              placeholder="Search specialties by name or code..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
            />
          </div>
          <Button type="submit" variant="secondary" size="md">
            <Search size={16} />
            Search
          </Button>
          {search && (
            <Button
              type="button"
              variant="outline"
              size="md"
              onClick={() => {
                setSearchInput('');
                setSearch('');
                setPage(0);
              }}
            >
              Clear
            </Button>
          )}
        </form>
      </Card>

      {/* Table Content */}
      <Card>
        {isLoading ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
            Loading specialties...
          </div>
        ) : isError ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#ef4444' }}>
            <p>Failed to load specialties: {(error as Error)?.message}</p>
            <Button variant="outline" size="sm" onClick={() => refetch()}>
              Retry
            </Button>
          </div>
        ) : !data || data.content.length === 0 ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
            No specialties found {search ? `matching "${search}"` : ''}.
          </div>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Specialty Name</TableHead>
                  <TableHead>Description</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((spec) => (
                  <TableRow key={spec.id}>
                    <TableCell style={{ fontWeight: 600 }}>{spec.code}</TableCell>
                    <TableCell>{spec.name}</TableCell>
                    <TableCell style={{ color: '#64748b', fontSize: '13px' }}>
                      {spec.description || '—'}
                    </TableCell>
                    <TableCell>
                      {spec.isActive ? (
                        <Badge variant="success">Active</Badge>
                      ) : (
                        <Badge variant="neutral">Inactive</Badge>
                      )}
                    </TableCell>
                    <TableCell style={{ textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', gap: '8px' }}>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => openEditModal(spec)}
                          title="Edit specialty"
                        >
                          <Edit2 size={14} />
                        </Button>
                        {spec.isActive && (
                          <Button
                            variant="danger"
                            size="sm"
                            onClick={() => setDeactivatingSpecialty(spec)}
                            title="Deactivate specialty"
                          >
                            <Ban size={14} />
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            <Pagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              onPageChange={(newPage) => setPage(newPage)}
            />
          </>
        )}
      </Card>

      {/* Create / Edit Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={closeModal}
        title={editingSpecialty ? `Edit Specialty (${editingSpecialty.code})` : 'Add New Specialty'}
      >
        <form onSubmit={handleSubmit(onFormSubmit)} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {actionError && (
            <div style={{ padding: '10px 14px', borderRadius: '8px', backgroundColor: '#fef2f2', color: '#991b1b', fontSize: '13px' }}>
              {actionError}
            </div>
          )}

          {!editingSpecialty && (
            <Input
              label="Specialty Code"
              placeholder="e.g. CARDIO"
              error={errors.code?.message}
              {...register('code')}
            />
          )}

          <Input
            label="Specialty Name"
            placeholder="e.g. Cardiology"
            error={errors.name?.message}
            {...register('name')}
          />

          <Input
            label="Description"
            placeholder="e.g. Disorders of the heart and blood vessels"
            error={errors.description?.message}
            {...register('description')}
          />

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
            <Button type="button" variant="outline" size="md" onClick={closeModal} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="md" disabled={isSubmitting}>
              {isSubmitting ? 'Saving...' : editingSpecialty ? 'Update Specialty' : 'Create Specialty'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Deactivate Confirm Dialog */}
      <ConfirmDialog
        isOpen={!!deactivatingSpecialty}
        title="Deactivate Specialty"
        message={`Are you sure you want to deactivate "${deactivatingSpecialty?.name}"? Deactivation will be rejected with 409 if active doctors are assigned to this specialty.`}
        confirmLabel="Deactivate Specialty"
        variant="danger"
        isLoading={deactivateMutation.isPending}
        onConfirm={() => {
          if (deactivatingSpecialty) {
            deactivateMutation.mutate(deactivatingSpecialty.id);
          }
        }}
        onCancel={() => setDeactivatingSpecialty(null)}
      />
    </div>
  );
};
