import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { apiRequest } from '../api/client';
import { Department, PageResponse } from '../types/masterData';
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

const departmentSchema = z.object({
  code: z.string().min(2, 'Code must be at least 2 characters').max(20),
  name: z.string().min(2, 'Department name is required'),
  description: z.string().optional(),
});

type DepartmentFormData = z.infer<typeof departmentSchema>;

export const DepartmentsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingDept, setEditingDept] = useState<Department | null>(null);

  const [deactivatingDept, setDeactivatingDept] = useState<Department | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<DepartmentFormData>();

  // Fetch paginated departments
  const { data, isLoading, isError, error, refetch } = useQuery<PageResponse<Department>>({
    queryKey: ['departments', page, search],
    queryFn: () =>
      apiRequest<PageResponse<Department>>(
        `/api/departments?page=${page}&size=10&search=${encodeURIComponent(search)}&activeOnly=false`
      ),
  });

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (newDept: DepartmentFormData) =>
      apiRequest<Department>('/api/departments', {
        method: 'POST',
        body: JSON.stringify(newDept),
      }),
    onSuccess: () => {
      toast.success('Department created successfully');
      queryClient.invalidateQueries({ queryKey: ['departments'] });
      closeModal();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.fieldErrors) {
        Object.entries(err.fieldErrors).forEach(([field, msg]) => {
          setError(field as keyof DepartmentFormData, { message: msg });
        });
      } else if (err instanceof ApiError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to create department');
      }
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<DepartmentFormData> }) =>
      apiRequest<Department>(`/api/departments/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),
    onSuccess: () => {
      toast.success('Department updated successfully');
      queryClient.invalidateQueries({ queryKey: ['departments'] });
      closeModal();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.fieldErrors) {
        Object.entries(err.fieldErrors).forEach(([field, msg]) => {
          setError(field as keyof DepartmentFormData, { message: msg });
        });
      } else if (err instanceof ApiError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to update department');
      }
    },
  });

  // Soft-delete / Deactivate mutation
  const deactivateMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/api/departments/${id}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      toast.success('Department deactivated successfully');
      queryClient.invalidateQueries({ queryKey: ['departments'] });
      setDeactivatingDept(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        toast.error(err.message);
      } else {
        toast.error('Failed to deactivate department');
      }
      setDeactivatingDept(null);
    },
  });

  const openCreateModal = () => {
    setEditingDept(null);
    setActionError(null);
    reset({
      code: '',
      name: '',
      description: '',
    });
    setIsModalOpen(true);
  };

  const openEditModal = (dept: Department) => {
    setEditingDept(dept);
    setActionError(null);
    reset({
      code: dept.code,
      name: dept.name,
      description: dept.description || '',
    });
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditingDept(null);
    setActionError(null);
  };

  const onFormSubmit = async (formData: DepartmentFormData) => {
    setActionError(null);
    const validation = departmentSchema.safeParse(formData);
    if (!validation.success) {
      for (const issue of validation.error.issues) {
        setError(issue.path[0] as keyof DepartmentFormData, { message: issue.message });
      }
      return;
    }

    if (editingDept) {
      await updateMutation.mutateAsync({ id: editingDept.id, data: formData });
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
            Hospital Departments
          </h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>
            Configure clinical and operational hospital departments
          </p>
        </div>
        <Button variant="primary" size="md" onClick={openCreateModal}>
          <Plus size={16} />
          Add Department
        </Button>
      </div>

      {/* Search Bar */}
      <Card style={{ marginBottom: '20px', padding: '16px' }}>
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
          <div style={{ flex: 1 }}>
            <Input
              type="text"
              placeholder="Search departments by name or code..."
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
            Loading departments...
          </div>
        ) : isError ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#ef4444' }}>
            <p>Failed to load departments: {(error as Error)?.message}</p>
            <Button variant="outline" size="sm" onClick={() => refetch()}>
              Retry
            </Button>
          </div>
        ) : !data || data.content.length === 0 ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
            No departments found {search ? `matching "${search}"` : ''}.
          </div>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Department Name</TableHead>
                  <TableHead>Description</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((dept) => (
                  <TableRow key={dept.id}>
                    <TableCell style={{ fontWeight: 600 }}>{dept.code}</TableCell>
                    <TableCell>{dept.name}</TableCell>
                    <TableCell style={{ color: '#64748b', fontSize: '13px' }}>
                      {dept.description || '—'}
                    </TableCell>
                    <TableCell>
                      {dept.isActive ? (
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
                          onClick={() => openEditModal(dept)}
                          title="Edit department"
                        >
                          <Edit2 size={14} />
                        </Button>
                        {dept.isActive && (
                          <Button
                            variant="danger"
                            size="sm"
                            onClick={() => setDeactivatingDept(dept)}
                            title="Deactivate department"
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
        title={editingDept ? `Edit Department (${editingDept.code})` : 'Add New Department'}
      >
        <form onSubmit={handleSubmit(onFormSubmit)} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {actionError && (
            <div style={{ padding: '10px 14px', borderRadius: '8px', backgroundColor: '#fef2f2', color: '#991b1b', fontSize: '13px' }}>
              {actionError}
            </div>
          )}

          {!editingDept && (
            <Input
              label="Department Code"
              placeholder="e.g. CARDIO"
              error={errors.code?.message}
              {...register('code')}
            />
          )}

          <Input
            label="Department Name"
            placeholder="e.g. Cardiology"
            error={errors.name?.message}
            {...register('name')}
          />

          <Input
            label="Description"
            placeholder="e.g. Outpatient cardiology and diagnostic care"
            error={errors.description?.message}
            {...register('description')}
          />

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
            <Button type="button" variant="outline" size="md" onClick={closeModal} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="md" disabled={isSubmitting}>
              {isSubmitting ? 'Saving...' : editingDept ? 'Update Department' : 'Create Department'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Deactivate Confirm Dialog */}
      <ConfirmDialog
        isOpen={!!deactivatingDept}
        title="Deactivate Department"
        message={`Are you sure you want to deactivate "${deactivatingDept?.name}"? Deactivation will be rejected with 409 if active hospital rooms reference this department.`}
        confirmLabel="Deactivate Department"
        variant="danger"
        isLoading={deactivateMutation.isPending}
        onConfirm={() => {
          if (deactivatingDept) {
            deactivateMutation.mutate(deactivatingDept.id);
          }
        }}
        onCancel={() => setDeactivatingDept(null)}
      />
    </div>
  );
};
