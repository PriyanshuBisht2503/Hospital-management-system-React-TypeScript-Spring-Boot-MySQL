import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { apiRequest } from '../api/client';
import { Room, Branch, Department, PageResponse } from '../types/masterData';
import { ApiError } from '../types/auth';
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
} from '@hms/ui';
import { Search, Plus, Edit2, Ban } from 'lucide-react';

const roomSchema = z.object({
  branchId: z.coerce.number().min(1, 'Please select a branch'),
  departmentId: z.coerce.number().min(1, 'Please select a department'),
  roomNumber: z.string().min(1, 'Room number is required'),
  roomType: z.string().min(1, 'Room type is required'),
  floorNumber: z.string().min(1, 'Floor number is required'),
});

type RoomFormData = z.infer<typeof roomSchema>;

const ROOM_TYPES = [
  { value: 'CONSULTATION', label: 'Consultation Room' },
  { value: 'ICU', label: 'Intensive Care Unit (ICU)' },
  { value: 'OPERATION_THEATER', label: 'Operation Theater' },
  { value: 'EMERGENCY', label: 'Emergency Room' },
  { value: 'GENERAL_WARD', label: 'General Ward' },
];

export const RoomsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingRoom, setEditingRoom] = useState<Room | null>(null);

  const [deactivatingRoom, setDeactivatingRoom] = useState<Room | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RoomFormData>();

  // Fetch paginated rooms
  const { data, isLoading, isError, error, refetch } = useQuery<PageResponse<Room>>({
    queryKey: ['rooms', page, search],
    queryFn: () =>
      apiRequest<PageResponse<Room>>(
        `/api/rooms?page=${page}&size=10&search=${encodeURIComponent(search)}&activeOnly=false`
      ),
  });

  // Fetch branches dropdown list
  const branchesQuery = useQuery<PageResponse<Branch>>({
    queryKey: ['active-branches-dropdown'],
    queryFn: () => apiRequest<PageResponse<Branch>>('/api/branches?size=100&activeOnly=true'),
  });

  // Fetch departments dropdown list
  const departmentsQuery = useQuery<PageResponse<Department>>({
    queryKey: ['active-departments-dropdown'],
    queryFn: () => apiRequest<PageResponse<Department>>('/api/departments?size=100&activeOnly=true'),
  });

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (newRoom: RoomFormData) =>
      apiRequest<Room>('/api/rooms', {
        method: 'POST',
        body: JSON.stringify(newRoom),
      }),
    onSuccess: () => {
      toast.success('Room created successfully');
      queryClient.invalidateQueries({ queryKey: ['rooms'] });
      closeModal();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.fieldErrors) {
        Object.entries(err.fieldErrors).forEach(([field, msg]) => {
          setError(field as keyof RoomFormData, { message: msg });
        });
      } else if (err instanceof ApiError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to create room');
      }
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: RoomFormData }) =>
      apiRequest<Room>(`/api/rooms/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      }),
    onSuccess: () => {
      toast.success('Room updated successfully');
      queryClient.invalidateQueries({ queryKey: ['rooms'] });
      closeModal();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.fieldErrors) {
        Object.entries(err.fieldErrors).forEach(([field, msg]) => {
          setError(field as keyof RoomFormData, { message: msg });
        });
      } else if (err instanceof ApiError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to update room');
      }
    },
  });

  // Soft-delete / Deactivate mutation
  const deactivateMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/api/rooms/${id}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      toast.success('Room deactivated successfully');
      queryClient.invalidateQueries({ queryKey: ['rooms'] });
      setDeactivatingRoom(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        toast.error(err.message);
      } else {
        toast.error('Failed to deactivate room');
      }
      setDeactivatingRoom(null);
    },
  });

  const openCreateModal = () => {
    setEditingRoom(null);
    setActionError(null);
    reset({
      branchId: branchesQuery.data?.content[0]?.id || 1,
      departmentId: departmentsQuery.data?.content[0]?.id || 1,
      roomNumber: '',
      roomType: 'CONSULTATION',
      floorNumber: 'Floor 1',
    });
    setIsModalOpen(true);
  };

  const openEditModal = (room: Room) => {
    setEditingRoom(room);
    setActionError(null);
    reset({
      branchId: room.branchId,
      departmentId: room.departmentId,
      roomNumber: room.roomNumber,
      roomType: room.roomType,
      floorNumber: room.floorNumber,
    });
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditingRoom(null);
    setActionError(null);
  };

  const onFormSubmit = async (formData: RoomFormData) => {
    setActionError(null);
    const validation = roomSchema.safeParse(formData);
    if (!validation.success) {
      for (const issue of validation.error.issues) {
        setError(issue.path[0] as keyof RoomFormData, { message: issue.message });
      }
      return;
    }

    if (editingRoom) {
      await updateMutation.mutateAsync({ id: editingRoom.id, data: formData });
    } else {
      await createMutation.mutateAsync(formData);
    }
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setSearch(searchInput);
  };

  const branchOptions = branchesQuery.data?.content.map((b) => ({
    value: b.id,
    label: `${b.name} (${b.city})`,
  })) || [];

  const departmentOptions = departmentsQuery.data?.content.map((d) => ({
    value: d.id,
    label: d.name,
  })) || [];

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 700, color: '#0f172a', margin: '0 0 4px' }}>
            Hospital Rooms & Facilities
          </h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>
            Manage consultation clinics, operation theaters, and inpatient wards
          </p>
        </div>
        <Button variant="primary" size="md" onClick={openCreateModal}>
          <Plus size={16} />
          Add Room
        </Button>
      </div>

      {/* Search Bar */}
      <Card style={{ marginBottom: '20px', padding: '16px' }}>
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
          <div style={{ flex: 1 }}>
            <Input
              type="text"
              placeholder="Search rooms by number or type..."
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
            Loading rooms...
          </div>
        ) : isError ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#ef4444' }}>
            <p>Failed to load rooms: {(error as Error)?.message}</p>
            <Button variant="outline" size="sm" onClick={() => refetch()}>
              Retry
            </Button>
          </div>
        ) : !data || data.content.length === 0 ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
            No rooms found {search ? `matching "${search}"` : ''}.
          </div>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Room #</TableHead>
                  <TableHead>Branch</TableHead>
                  <TableHead>Department</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Floor</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((room) => (
                  <TableRow key={room.id}>
                    <TableCell style={{ fontWeight: 600 }}>{room.roomNumber}</TableCell>
                    <TableCell>{room.branchName}</TableCell>
                    <TableCell>{room.departmentName}</TableCell>
                    <TableCell>
                      <Badge variant="neutral">{room.roomType}</Badge>
                    </TableCell>
                    <TableCell style={{ color: '#64748b', fontSize: '13px' }}>{room.floorNumber}</TableCell>
                    <TableCell>
                      {room.isActive ? (
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
                          onClick={() => openEditModal(room)}
                          title="Edit room"
                        >
                          <Edit2 size={14} />
                        </Button>
                        {room.isActive && (
                          <Button
                            variant="danger"
                            size="sm"
                            onClick={() => setDeactivatingRoom(room)}
                            title="Deactivate room"
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
        title={editingRoom ? `Edit Room ${editingRoom.roomNumber}` : 'Add New Hospital Room'}
      >
        <form onSubmit={handleSubmit(onFormSubmit)} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {actionError && (
            <div style={{ padding: '10px 14px', borderRadius: '8px', backgroundColor: '#fef2f2', color: '#991b1b', fontSize: '13px' }}>
              {actionError}
            </div>
          )}

          <Select
            label="Hospital Branch"
            placeholder="Select Branch..."
            options={branchOptions}
            error={errors.branchId?.message}
            {...register('branchId')}
          />

          <Select
            label="Department"
            placeholder="Select Department..."
            options={departmentOptions}
            error={errors.departmentId?.message}
            {...register('departmentId')}
          />

          <Input
            label="Room Number / Identifier"
            placeholder="e.g. 101-A"
            error={errors.roomNumber?.message}
            {...register('roomNumber')}
          />

          <Select
            label="Room Type"
            options={ROOM_TYPES}
            error={errors.roomType?.message}
            {...register('roomType')}
          />

          <Input
            label="Floor Location"
            placeholder="e.g. Ground Floor, Wing B"
            error={errors.floorNumber?.message}
            {...register('floorNumber')}
          />

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
            <Button type="button" variant="outline" size="md" onClick={closeModal} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="md" disabled={isSubmitting}>
              {isSubmitting ? 'Saving...' : editingRoom ? 'Update Room' : 'Create Room'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Deactivate Confirm Dialog */}
      <ConfirmDialog
        isOpen={!!deactivatingRoom}
        title="Deactivate Room"
        message={`Are you sure you want to deactivate room "${deactivatingRoom?.roomNumber}" in ${deactivatingRoom?.branchName}?`}
        confirmLabel="Deactivate Room"
        variant="danger"
        isLoading={deactivateMutation.isPending}
        onConfirm={() => {
          if (deactivatingRoom) {
            deactivateMutation.mutate(deactivatingRoom.id);
          }
        }}
        onCancel={() => setDeactivatingRoom(null)}
      />
    </div>
  );
};
