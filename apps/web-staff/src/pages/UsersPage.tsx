import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { apiRequest } from '../api/client';
import { User, Role, StaffRole, ApiError } from '../types/auth';
import { PageResponse } from '../types/masterData';
import {
  Button,
  Card,
  Input,
  Select,
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
  DEFAULT_PAGE_SIZE,
} from '@hms/ui';
import { Search, Plus, ShieldAlert, UserCog, Power } from 'lucide-react';
import { UserStatusBadge } from '../components/UserStatusBadge';

const createStaffSchema = z.object({
  fullName: z.string().min(2, 'Full name is required'),
  email: z.string().email('Please enter a valid email address'),
  phone: z.string().min(7, 'Phone number is required'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  role: z.enum(['ADMIN', 'DOCTOR', 'RECEPTIONIST']),
});

type CreateStaffFormData = z.infer<typeof createStaffSchema>;

const STAFF_ROLE_OPTIONS = [
  { value: 'ADMIN', label: 'Administrator (ADMIN)' },
  { value: 'DOCTOR', label: 'Physician / Doctor (DOCTOR)' },
  { value: 'RECEPTIONIST', label: 'Front Desk (RECEPTIONIST)' },
];

export const UsersPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [roleFilter, setRoleFilter] = useState<string>('');

  // Modals & Dialogs
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [roleChangeUser, setRoleChangeUser] = useState<User | null>(null);
  const [newSelectedRole, setNewSelectedRole] = useState<StaffRole>('RECEPTIONIST');
  const [statusChangeUser, setStatusChangeUser] = useState<User | null>(null);

  const [actionError, setActionError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateStaffFormData>();

  // Fetch paginated users
  const { data, isLoading, isError, error, refetch } = useQuery<PageResponse<User>>({
    queryKey: ['admin-users', page, search, roleFilter, DEFAULT_PAGE_SIZE],
    queryFn: () => {
      let url = `/api/admin/users?page=${page}&size=${DEFAULT_PAGE_SIZE}&search=${encodeURIComponent(search)}`;
      if (roleFilter) {
        url += `&role=${encodeURIComponent(roleFilter)}`;
      }
      return apiRequest<PageResponse<User>>(url);
    },
  });

  // Create Staff Mutation
  const createMutation = useMutation({
    mutationFn: (newStaff: CreateStaffFormData) =>
      apiRequest<User>('/api/admin/users', {
        method: 'POST',
        body: JSON.stringify(newStaff),
      }),
    onSuccess: (createdUser) => {
      toast.success(`Staff account created for ${createdUser.fullName}`);
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      closeCreateModal();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.fieldErrors) {
        Object.entries(err.fieldErrors).forEach(([field, msg]) => {
          setError(field as keyof CreateStaffFormData, { message: msg });
        });
      } else if (err instanceof ApiError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to create staff member');
      }
    },
  });

  // Change Role Mutation
  const changeRoleMutation = useMutation({
    mutationFn: ({ id, role }: { id: number; role: StaffRole }) =>
      apiRequest<User>(`/api/admin/users/${id}/role`, {
        method: 'PATCH',
        body: JSON.stringify({ role }),
      }),
    onSuccess: (updated) => {
      toast.success(`Updated role for ${updated.fullName} to ${updated.role}`);
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      setRoleChangeUser(null);
      setActionError(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setActionError(err.message);
        toast.error(err.message);
      } else {
        toast.error('Failed to change user role');
      }
    },
  });

  // Enable / Disable Status Mutation
  const toggleStatusMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      apiRequest<User>(`/api/admin/users/${id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ enabled }),
      }),
    onSuccess: (updated) => {
      toast.success(`${updated.fullName} is now ${updated.enabled ? 'Active' : 'Disabled'}`);
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      setStatusChangeUser(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        toast.error(err.message);
      } else {
        toast.error('Failed to update user status');
      }
      setStatusChangeUser(null);
    },
  });

  const openCreateModal = () => {
    setActionError(null);
    reset({
      fullName: '',
      email: '',
      phone: '',
      password: '',
      role: 'DOCTOR',
    });
    setIsCreateModalOpen(true);
  };

  const closeCreateModal = () => {
    setIsCreateModalOpen(false);
    setActionError(null);
  };

  const onCreateFormSubmit = async (formData: CreateStaffFormData) => {
    setActionError(null);
    const validation = createStaffSchema.safeParse(formData);
    if (!validation.success) {
      for (const issue of validation.error.issues) {
        setError(issue.path[0] as keyof CreateStaffFormData, { message: issue.message });
      }
      return;
    }

    await createMutation.mutateAsync(formData);
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setSearch(searchInput);
  };

  const getRoleBadgeVariant = (role: Role) => {
    switch (role) {
      case 'ADMIN': return 'danger';
      case 'DOCTOR': return 'info';
      case 'RECEPTIONIST': return 'neutral';
      case 'PATIENT': return 'neutral';
    }
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>
            User & Staff Management
          </h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>
            Manage administrative personnel, clinical doctors, receptionists, and patient accounts
          </p>
        </div>
        <Button variant="primary" size="md" onClick={openCreateModal}>
          <Plus size={16} />
          Create Staff Member
        </Button>
      </div>

      {/* Filters Bar */}
      <Card style={{ marginBottom: '20px', padding: '16px' }}>
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: '240px' }}>
            <Input
              type="text"
              placeholder="Search by name, email, or phone..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
            />
          </div>

          <div style={{ width: '180px' }}>
            <select
              value={roleFilter}
              onChange={(e) => {
                setRoleFilter(e.target.value);
                setPage(0);
              }}
              style={{
                width: '100%',
                padding: '10px 14px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                fontSize: '14px',
                color: '#0f172a',
                outline: 'none',
                backgroundColor: '#ffffff',
                cursor: 'pointer',
              }}
            >
              <option value="">All Roles</option>
              <option value="ADMIN">ADMIN</option>
              <option value="DOCTOR">DOCTOR</option>
              <option value="RECEPTIONIST">RECEPTIONIST</option>
              <option value="PATIENT">PATIENT</option>
            </select>
          </div>

          <Button type="submit" variant="secondary" size="md">
            <Search size={16} />
            Search
          </Button>

          {(search || roleFilter) && (
            <Button
              type="button"
              variant="outline"
              size="md"
              onClick={() => {
                setSearchInput('');
                setSearch('');
                setRoleFilter('');
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
            Loading users...
          </div>
        ) : isError ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#ef4444' }}>
            <p>Failed to load users: {(error as Error)?.message}</p>
            <Button variant="outline" size="sm" onClick={() => refetch()}>
              Retry
            </Button>
          </div>
        ) : !data || data.content.length === 0 ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
            No users found {search ? `matching "${search}"` : ''}.
          </div>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>User / Name</TableHead>
                  <TableHead>Contact</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead>Account Status</TableHead>
                  <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((user) => (
                  <TableRow key={user.id}>
                    <TableCell>
                      <div style={{ fontWeight: 600, color: '#0f172a' }}>{user.fullName}</div>
                      <div style={{ fontSize: '12px', color: '#64748b' }}>{user.email}</div>
                    </TableCell>
                    <TableCell style={{ fontSize: '13px' }}>{user.phone || '—'}</TableCell>
                    <TableCell>
                      <Badge variant={getRoleBadgeVariant(user.role)}>
                        {user.role}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <UserStatusBadge enabled={user.enabled} />
                    </TableCell>
                    <TableCell style={{ textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', gap: '8px' }}>
                        {user.role !== 'PATIENT' && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => {
                              setActionError(null);
                              setRoleChangeUser(user);
                              setNewSelectedRole(user.role as StaffRole);
                            }}
                            title="Change role"
                          >
                            <UserCog size={14} />
                          </Button>
                        )}

                        <Button
                          variant={user.enabled ? 'danger' : 'outline'}
                          size="sm"
                          onClick={() => setStatusChangeUser(user)}
                          title={user.enabled ? 'Disable account' : 'Enable account'}
                        >
                          <Power size={14} />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            <Pagination
              pageNumber={data.pageNumber}
              pageSize={data.pageSize}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              isFirst={data.isFirst}
              isLast={data.isLast}
              onPageChange={(newPage) => setPage(newPage)}
            />
          </>
        )}
      </Card>

      {/* Create Staff Modal */}
      <Modal
        isOpen={isCreateModalOpen}
        onClose={closeCreateModal}
        title="Create New Staff Member"
      >
        <form onSubmit={handleSubmit(onCreateFormSubmit)} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {actionError && (
            <div style={{ padding: '10px 14px', borderRadius: '8px', backgroundColor: '#fef2f2', color: '#991b1b', fontSize: '13px' }}>
              {actionError}
            </div>
          )}

          <Input
            label="Full Name"
            placeholder="e.g. Dr. Sarah Jenkins"
            error={errors.fullName?.message}
            {...register('fullName')}
          />

          <Input
            label="Staff Email Address"
            type="email"
            placeholder="e.g. s.jenkins@example.com"
            error={errors.email?.message}
            {...register('email')}
          />

          <Input
            label="Phone Number"
            placeholder="e.g. +971501234567"
            error={errors.phone?.message}
            {...register('phone')}
          />

          <Input
            label="Initial Password (min 8 characters)"
            type="password"
            placeholder="••••••••"
            error={errors.password?.message}
            {...register('password')}
          />

          <Select
            label="Assigned Staff Role"
            options={STAFF_ROLE_OPTIONS}
            error={errors.role?.message}
            {...register('role')}
          />

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
            <Button type="button" variant="outline" size="md" onClick={closeCreateModal} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="md" disabled={isSubmitting}>
              {isSubmitting ? 'Creating...' : 'Create Staff Member'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Change Role Modal */}
      <Modal
        isOpen={!!roleChangeUser}
        onClose={() => setRoleChangeUser(null)}
        title={`Change Role: ${roleChangeUser?.fullName}`}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {actionError && (
            <div style={{ padding: '10px 14px', borderRadius: '8px', backgroundColor: '#fef2f2', color: '#991b1b', fontSize: '13px' }}>
              <ShieldAlert size={16} style={{ display: 'inline', marginRight: '6px' }} />
              {actionError}
            </div>
          )}

          <p style={{ margin: 0, fontSize: '14px', color: '#64748b' }}>
            Current role: <strong>{roleChangeUser?.role}</strong>. Changing role immediately revokes all existing refresh tokens for this user.
          </p>

          <Select
            label="New Staff Role"
            value={newSelectedRole}
            onChange={(e) => setNewSelectedRole(e.target.value as StaffRole)}
            options={STAFF_ROLE_OPTIONS}
          />

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '8px' }}>
            <Button
              type="button"
              variant="outline"
              size="md"
              onClick={() => setRoleChangeUser(null)}
              disabled={changeRoleMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              type="button"
              variant="primary"
              size="md"
              disabled={changeRoleMutation.isPending || newSelectedRole === roleChangeUser?.role}
              onClick={() => {
                if (roleChangeUser) {
                  changeRoleMutation.mutate({ id: roleChangeUser.id, role: newSelectedRole });
                }
              }}
            >
              {changeRoleMutation.isPending ? 'Updating...' : 'Confirm Role Change'}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Enable / Disable Confirm Dialog */}
      <ConfirmDialog
        isOpen={!!statusChangeUser}
        title={statusChangeUser?.enabled ? 'Disable User Account' : 'Enable User Account'}
        message={
          statusChangeUser?.enabled
            ? `Are you sure you want to disable ${statusChangeUser.fullName}? This will revoke all active refresh tokens and immediately prevent them from logging in.`
            : `Are you sure you want to re-enable access for ${statusChangeUser?.fullName}?`
        }
        confirmLabel={statusChangeUser?.enabled ? 'Disable Account' : 'Enable Account'}
        variant={statusChangeUser?.enabled ? 'danger' : 'primary'}
        isLoading={toggleStatusMutation.isPending}
        onConfirm={() => {
          if (statusChangeUser) {
            toggleStatusMutation.mutate({
              id: statusChangeUser.id,
              enabled: !statusChangeUser.enabled,
            });
          }
        }}
        onCancel={() => setStatusChangeUser(null)}
      />
    </div>
  );
};

