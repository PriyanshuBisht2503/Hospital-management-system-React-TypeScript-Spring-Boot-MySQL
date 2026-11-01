import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { apiRequest } from '../api/client';
import { Branch, PageResponse } from '../types/masterData';
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

const branchSchema = z.object({
  code: z.string().min(2, 'Code must be at least 2 characters').max(20),
  name: z.string().min(2, 'Branch name is required'),
  city: z.string().min(2, 'City is required'),
  address: z.string().optional(),
  phone: z.string().optional(),
  email: z.string().email('Invalid email').optional().or(z.literal('')),
});

type BranchFormData = z.infer<typeof branchSchema>;

export const BranchesPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingBranch, setEditingBranch] = useState<Branch | null>(null);

  const [deactivatingBranch, setDeactivatingBranch] = useState<Branch | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<BranchFormData>();

  // Fetch paginated branches
  const { data, isLoading, isError, error, refetch } = useQuery<PageResponse<Branch>>({
    queryKey: ['branches', page, search],
    queryFn: () =>
      apiRequest<PageResponse<Branch>>(
        `/api/branches?page=${page}&size=10&search=${encodeURIComponent(search)}&activeOnly=false`
      ),
  });

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (newBranch: BranchFormData) =>
      apiRequest<Branch>('/api/branches', {
        method: 'POST',
        body: JSON.stringify(newBranch),
      }),
    onSuccess: () => {
      toast.success('Branch created successfully');
      queryClient.invalidateQueries({ queryKey: ['branches'] });
      closeModal();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.fieldErrors) {
        Object.entries(err.fieldErrors).forEach(([field, msg]) => {
          setError(field as keyof BranchFormData, { message: msg });
        });
      } else if (err instanceof ApiError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to create branch');
      }
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<BranchFormData> }) =>
      apiRequest<Branch>(`/api/branches/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),
    onSuccess: () => {
      toast.success('Branch updated successfully');
      queryClient.invalidateQueries({ queryKey: ['branches'] });
      closeModal();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.fieldErrors) {
        Object.entries(err.fieldErrors).forEach(([field, msg]) => {
          setError(field as keyof BranchFormData, { message: msg });
        });
      } else if (err instanceof ApiError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to update branch');
      }
    },
  });

  // Soft-delete / Deactivate mutation
  const deactivateMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/api/branches/${id}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      toast.success('Branch deactivated successfully');
      queryClient.invalidateQueries({ queryKey: ['branches'] });
      setDeactivatingBranch(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        toast.error(err.message);
      } else {
        toast.error('Failed to deactivate branch');
      }
      setDeactivatingBranch(null);
    },
  });

  const openCreateModal = () => {
    setEditingBranch(null);
    setActionError(null);
    reset({
      code: '',
      name: '',
      city: 'Dubai',
      address: '',
      phone: '',
      email: '',
    });
    setIsModalOpen(true);
  };

  const openEditModal = (branch: Branch) => {
    setEditingBranch(branch);
    setActionError(null);
    reset({
      code: branch.code,
      name: branch.name,
      city: branch.city,
      address: branch.address || '',
      phone: branch.phone || '',
      email: branch.email || '',
    });
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditingBranch(null);
    setActionError(null);
  };

  const onFormSubmit = async (formData: BranchFormData) => {
    setActionError(null);
    const validation = branchSchema.safeParse(formData);
    if (!validation.success) {
      for (const issue of validation.error.issues) {
        setError(issue.path[0] as keyof BranchFormData, { message: issue.message });
      }
      return;
    }

    if (editingBranch) {
      await updateMutation.mutateAsync({ id: editingBranch.id, data: formData });
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
            Hospital Branches
          </h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>
            Manage hospital branches and physical healthcare campus locations
          </p>
        </div>
        <Button variant="primary" size="md" onClick={openCreateModal}>
          <Plus size={16} />
          Add Branch
        </Button>
      </div>

      {/* Search Bar */}
      <Card style={{ marginBottom: '20px', padding: '16px' }}>
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
          <div style={{ flex: 1 }}>
            <Input
              type="text"
              placeholder="Search branches by name, code, or city..."
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
            Loading hospital branches...
          </div>
        ) : isError ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#ef4444' }}>
            <p>Failed to load branches: {(error as Error)?.message}</p>
            <Button variant="outline" size="sm" onClick={() => refetch()}>
              Retry
            </Button>
          </div>
        ) : !data || data.content.length === 0 ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
            No branches found {search ? `matching "${search}"` : ''}.
          </div>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Branch Name</TableHead>
                  <TableHead>City</TableHead>
                  <TableHead>Contact</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((branch) => (
                  <TableRow key={branch.id}>
                    <TableCell style={{ fontWeight: 600 }}>{branch.code}</TableCell>
                    <TableCell>{branch.name}</TableCell>
                    <TableCell>{branch.city}</TableCell>
                    <TableCell>
                      <div style={{ fontSize: '12px' }}>
                        {branch.phone && <div>{branch.phone}</div>}
                        {branch.email && <div style={{ color: '#64748b' }}>{branch.email}</div>}
                      </div>
                    </TableCell>
                    <TableCell>
                      {branch.isActive ? (
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
                          onClick={() => openEditModal(branch)}
                          title="Edit branch"
                        >
                          <Edit2 size={14} />
                        </Button>
                        {branch.isActive && (
                          <Button
                            variant="danger"
                            size="sm"
                            onClick={() => setDeactivatingBranch(branch)}
                            title="Deactivate branch"
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
        title={editingBranch ? `Edit Branch (${editingBranch.code})` : 'Add New Branch'}
      >
        <form onSubmit={handleSubmit(onFormSubmit)} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {actionError && (
            <div style={{ padding: '10px 14px', borderRadius: '8px', backgroundColor: '#fef2f2', color: '#991b1b', fontSize: '13px' }}>
              {actionError}
            </div>
          )}

          {!editingBranch && (
            <Input
              label="Branch Code"
              placeholder="e.g. MANKHOOL"
              error={errors.code?.message}
              {...register('code')}
            />
          )}

          <Input
            label="Branch Name"
            placeholder="e.g. CareHMS Main Campus"
            error={errors.name?.message}
            {...register('name')}
          />

          <Input
            label="City"
            placeholder="e.g. Dubai"
            error={errors.city?.message}
            {...register('city')}
          />

          <Input
            label="Address"
            placeholder="e.g. Kuwait Road, Al Mankhool"
            error={errors.address?.message}
            {...register('address')}
          />

          <Input
            label="Phone Number"
            placeholder="e.g. +97144400500"
            error={errors.phone?.message}
            {...register('phone')}
          />

          <Input
            label="Email Address"
            type="email"
            placeholder="e.g. branch@example.com"
            error={errors.email?.message}
            {...register('email')}
          />

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
            <Button type="button" variant="outline" size="md" onClick={closeModal} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="md" disabled={isSubmitting}>
              {isSubmitting ? 'Saving...' : editingBranch ? 'Update Branch' : 'Create Branch'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Deactivate Confirm Dialog */}
      <ConfirmDialog
        isOpen={!!deactivatingBranch}
        title="Deactivate Branch"
        message={`Are you sure you want to deactivate "${deactivatingBranch?.name}"? Deactivation will be rejected with 409 if active doctors or rooms reference this branch.`}
        confirmLabel="Deactivate Branch"
        variant="danger"
        isLoading={deactivateMutation.isPending}
        onConfirm={() => {
          if (deactivatingBranch) {
            deactivateMutation.mutate(deactivatingBranch.id);
          }
        }}
        onCancel={() => setDeactivatingBranch(null)}
      />
    </div>
  );
};

