import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { apiRequest } from '../api/client';
import { Branch, Specialty, PageResponse } from '../types/masterData';
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
  DEFAULT_PAGE_SIZE,
} from '@hms/ui';
import { Search, Plus, Edit2, Ban, Upload, Stethoscope, Building2 } from 'lucide-react';

export interface DoctorAdmin {
  id: number;
  userId: number;
  email: string;
  phone: string;
  fullName: string;
  bio?: string;
  qualifications?: string;
  languages?: string;
  experienceYears: number;
  photoUrl?: string;
  consultationFee: number;
  specialties: Specialty[];
  branches: Branch[];
  isActive: boolean;
}

const doctorCreateSchema = z.object({
  fullName: z.string().min(2, 'Doctor name is required'),
  email: z.string().email('Valid email address is required'),
  phone: z.string().min(7, 'Phone number is required'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  consultationFee: z.coerce.number().min(0, 'Fee cannot be negative'),
  experienceYears: z.coerce.number().min(0, 'Experience cannot be negative'),
  qualifications: z.string().optional(),
  languages: z.string().optional(),
  bio: z.string().optional(),
});

type DoctorCreateFormData = z.infer<typeof doctorCreateSchema>;

const doctorEditSchema = z.object({
  fullName: z.string().min(2, 'Doctor name is required'),
  phone: z.string().min(7, 'Phone number is required'),
  consultationFee: z.coerce.number().min(0, 'Fee cannot be negative'),
  experienceYears: z.coerce.number().min(0, 'Experience cannot be negative'),
  qualifications: z.string().optional(),
  languages: z.string().optional(),
  bio: z.string().optional(),
});

type DoctorEditFormData = z.infer<typeof doctorEditSchema>;

export const DoctorsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [branchFilter, setBranchFilter] = useState<string>('');
  const [specialtyFilter, setSpecialtyFilter] = useState<string>('');

  // Modals & State
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [editingDoctor, setEditingDoctor] = useState<DoctorAdmin | null>(null);
  const [deactivatingDoctor, setDeactivatingDoctor] = useState<DoctorAdmin | null>(null);

  // Form Selections
  const [selectedSpecialtyIds, setSelectedSpecialtyIds] = useState<number[]>([]);
  const [selectedBranchIds, setSelectedBranchIds] = useState<number[]>([]);
  const [photoUrl, setPhotoUrl] = useState<string>('');
  const [isUploadingPhoto, setIsUploadingPhoto] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const {
    register: registerCreate,
    handleSubmit: handleSubmitCreate,
    reset: resetCreate,
    setError: setErrorCreate,
    formState: { errors: errorsCreate, isSubmitting: isSubmittingCreate },
  } = useForm<DoctorCreateFormData>();

  const {
    register: registerEdit,
    handleSubmit: handleSubmitEdit,
    reset: resetEdit,
    setError: setErrorEdit,
    formState: { errors: errorsEdit, isSubmitting: isSubmittingEdit },
  } = useForm<DoctorEditFormData>();

  // Fetch doctors
  const { data, isLoading, isError, error, refetch } = useQuery<PageResponse<DoctorAdmin>>({
    queryKey: ['admin-doctors', page, search, branchFilter, specialtyFilter, DEFAULT_PAGE_SIZE],
    queryFn: () => {
      let url = `/api/admin/doctors?page=${page}&size=${DEFAULT_PAGE_SIZE}&search=${encodeURIComponent(search)}`;
      if (branchFilter) url += `&branchId=${branchFilter}`;
      if (specialtyFilter) url += `&specialtyId=${specialtyFilter}`;
      return apiRequest<PageResponse<DoctorAdmin>>(url);
    },
  });

  // Dropdowns
  const branchesQuery = useQuery<PageResponse<Branch>>({
    queryKey: ['active-branches-list'],
    queryFn: () => apiRequest<PageResponse<Branch>>('/api/branches?size=100&activeOnly=true'),
  });

  const specialtiesQuery = useQuery<PageResponse<Specialty>>({
    queryKey: ['active-specialties-list'],
    queryFn: () => apiRequest<PageResponse<Specialty>>('/api/specialties?size=100&activeOnly=true'),
  });

  // Photo Upload Handler
  const handlePhotoUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 5 * 1024 * 1024) {
      toast.error('File size exceeds maximum limit of 5 MB');
      return;
    }

    setIsUploadingPhoto(true);
    const formData = new FormData();
    formData.append('file', file);

    try {
      let uploadEndpoint = '/api/admin/doctors/photo';
      if (editingDoctor) {
        uploadEndpoint = `/api/admin/doctors/${editingDoctor.id}/photo`;
      }

      const res = await apiRequest<{ storedName: string; isPublic: boolean }>(uploadEndpoint, {
        method: 'POST',
        body: formData,
      });

      const url = `/api/files/${res.storedName}`;
      setPhotoUrl(url);
      toast.success('Doctor photo uploaded successfully');
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        toast.error(err.message);
      } else {
        toast.error('Failed to upload doctor photo');
      }
    } finally {
      setIsUploadingPhoto(false);
    }
  };

  // Create Mutation
  const createMutation = useMutation({
    mutationFn: (payload: unknown) =>
      apiRequest<DoctorAdmin>('/api/admin/doctors', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    onSuccess: (newDoc) => {
      toast.success(`Doctor profile created for Dr. ${newDoc.fullName}`);
      queryClient.invalidateQueries({ queryKey: ['admin-doctors'] });
      closeCreateModal();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.fieldErrors) {
        Object.entries(err.fieldErrors).forEach(([field, msg]) => {
          setErrorCreate(field as keyof DoctorCreateFormData, { message: msg });
        });
      } else if (err instanceof ApiError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to create doctor profile');
      }
    },
  });

  // Update Mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: unknown }) =>
      apiRequest<DoctorAdmin>(`/api/admin/doctors/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      }),
    onSuccess: (updated) => {
      toast.success(`Updated profile for Dr. ${updated.fullName}`);
      queryClient.invalidateQueries({ queryKey: ['admin-doctors'] });
      closeEditModal();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError && err.fieldErrors) {
        Object.entries(err.fieldErrors).forEach(([field, msg]) => {
          setErrorEdit(field as keyof DoctorEditFormData, { message: msg });
        });
      } else if (err instanceof ApiError) {
        setActionError(err.message);
      } else {
        setActionError('Failed to update doctor profile');
      }
    },
  });

  // Deactivate Mutation
  const deactivateMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<{ message: string }>(`/api/admin/doctors/${id}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      toast.success('Doctor profile deactivated and linked user disabled');
      if (data && data.content.length === 1 && page > 0) {
        setPage((p) => Math.max(0, p - 1));
      }
      queryClient.invalidateQueries({ queryKey: ['admin-doctors'] });
      setDeactivatingDoctor(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        toast.error(err.message);
      } else {
        toast.error('Failed to deactivate doctor');
      }
      setDeactivatingDoctor(null);
    },
  });

  const openCreateModal = () => {
    setActionError(null);
    setPhotoUrl('');
    setSelectedSpecialtyIds([]);
    setSelectedBranchIds([]);
    resetCreate({
      fullName: '',
      email: '',
      phone: '',
      password: '',
      consultationFee: 300,
      experienceYears: 5,
      qualifications: 'MBBS, MD',
      languages: 'English, Arabic',
      bio: '',
    });
    setIsCreateModalOpen(true);
  };

  const closeCreateModal = () => {
    setIsCreateModalOpen(false);
    setActionError(null);
  };

  const openEditModal = (doctor: DoctorAdmin) => {
    setEditingDoctor(doctor);
    setActionError(null);
    setPhotoUrl(doctor.photoUrl || '');
    setSelectedSpecialtyIds(doctor.specialties.map((s) => s.id));
    setSelectedBranchIds(doctor.branches.map((b) => b.id));
    resetEdit({
      fullName: doctor.fullName,
      phone: doctor.phone,
      consultationFee: doctor.consultationFee,
      experienceYears: doctor.experienceYears,
      qualifications: doctor.qualifications || '',
      languages: doctor.languages || '',
      bio: doctor.bio || '',
    });
  };

  const closeEditModal = () => {
    setEditingDoctor(null);
    setActionError(null);
  };

  const onCreateSubmit = async (formData: DoctorCreateFormData) => {
    setActionError(null);
    const validation = doctorCreateSchema.safeParse(formData);
    if (!validation.success) {
      for (const issue of validation.error.issues) {
        setErrorCreate(issue.path[0] as keyof DoctorCreateFormData, { message: issue.message });
      }
      return;
    }

    const payload = {
      ...formData,
      photoUrl: photoUrl || null,
      specialtyIds: selectedSpecialtyIds,
      branchIds: selectedBranchIds,
    };

    try {
      await createMutation.mutateAsync(payload);
    } catch {
      // Handled in the mutation's onError (field errors + general message).
    }
  };

  const onEditSubmit = async (formData: DoctorEditFormData) => {
    setActionError(null);
    const validation = doctorEditSchema.safeParse(formData);
    if (!validation.success) {
      for (const issue of validation.error.issues) {
        setErrorEdit(issue.path[0] as keyof DoctorEditFormData, { message: issue.message });
      }
      return;
    }

    const payload = {
      ...formData,
      photoUrl: photoUrl || null,
      specialtyIds: selectedSpecialtyIds,
      branchIds: selectedBranchIds,
    };

    if (editingDoctor) {
      try {
        await updateMutation.mutateAsync({ id: editingDoctor.id, payload });
      } catch {
        // Handled in the mutation's onError (field errors + general message).
      }
    }
  };

  const toggleSpecialty = (id: number) => {
    setSelectedSpecialtyIds((prev) =>
      prev.includes(id) ? prev.filter((sId) => sId !== id) : [...prev, id]
    );
  };

  const toggleBranch = (id: number) => {
    setSelectedBranchIds((prev) =>
      prev.includes(id) ? prev.filter((bId) => bId !== id) : [...prev, id]
    );
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
            Doctor Profiles & Credentials
          </h1>
          <p style={{ fontSize: '14px', color: '#64748b', margin: 0 }}>
            Manage clinical physician credentials, branches, medical specialties, and public profiles
          </p>
        </div>
        <Button variant="primary" size="md" onClick={openCreateModal}>
          <Plus size={16} />
          Create Doctor
        </Button>
      </div>

      {/* Filter Bar */}
      <Card style={{ marginBottom: '20px', padding: '16px' }}>
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: '220px' }}>
            <Input
              type="text"
              placeholder="Search doctors by name, email, or qualification..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
            />
          </div>

          <div style={{ width: '180px' }}>
            <select
              value={branchFilter}
              onChange={(e) => {
                setBranchFilter(e.target.value);
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
              <option value="">All Branches</option>
              {branchesQuery.data?.content.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                </option>
              ))}
            </select>
          </div>

          <div style={{ width: '180px' }}>
            <select
              value={specialtyFilter}
              onChange={(e) => {
                setSpecialtyFilter(e.target.value);
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
              <option value="">All Specialties</option>
              {specialtiesQuery.data?.content.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>

          <Button type="submit" variant="secondary" size="md">
            <Search size={16} />
            Search
          </Button>

          {(search || branchFilter || specialtyFilter) && (
            <Button
              type="button"
              variant="outline"
              size="md"
              onClick={() => {
                setSearchInput('');
                setSearch('');
                setBranchFilter('');
                setSpecialtyFilter('');
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
            Loading doctor profiles...
          </div>
        ) : isError ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#ef4444' }}>
            <p>Failed to load doctors: {(error as Error)?.message}</p>
            <Button variant="outline" size="sm" onClick={() => refetch()}>
              Retry
            </Button>
          </div>
        ) : !data || data.content.length === 0 ? (
          <div style={{ padding: '48px', textAlign: 'center', color: '#64748b' }}>
            No doctor profiles found {search ? `matching "${search}"` : ''}.
          </div>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Doctor</TableHead>
                  <TableHead>Specialties</TableHead>
                  <TableHead>Branches</TableHead>
                  <TableHead>Experience</TableHead>
                  <TableHead>Fee (AED)</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((doc) => (
                  <TableRow key={doc.id}>
                    <TableCell>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        {doc.photoUrl ? (
                          <img
                            src={doc.photoUrl}
                            alt={doc.fullName}
                            style={{
                              width: '40px',
                              height: '40px',
                              borderRadius: '50%',
                              objectFit: 'cover',
                              border: '1px solid #e2e8f0',
                            }}
                          />
                        ) : (
                          <div
                            style={{
                              width: '40px',
                              height: '40px',
                              borderRadius: '50%',
                              backgroundColor: '#e0f2fe',
                              color: '#0369a1',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              fontWeight: 700,
                              fontSize: '14px',
                            }}
                          >
                            Dr
                          </div>
                        )}
                        <div>
                          <div style={{ fontWeight: 600, color: '#0f172a' }}>{doc.fullName}</div>
                          <div style={{ fontSize: '12px', color: '#64748b' }}>{doc.email}</div>
                          {doc.qualifications && (
                            <div style={{ fontSize: '11px', color: '#94a3b8' }}>{doc.qualifications}</div>
                          )}
                        </div>
                      </div>
                    </TableCell>

                    <TableCell>
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', maxWidth: '200px' }}>
                        {doc.specialties.map((s) => (
                          <Badge key={s.id} variant="info">
                            {s.name}
                          </Badge>
                        ))}
                      </div>
                    </TableCell>

                    <TableCell>
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', maxWidth: '200px' }}>
                        {doc.branches.map((b) => (
                          <Badge key={b.id} variant="neutral">
                            {b.name}
                          </Badge>
                        ))}
                      </div>
                    </TableCell>

                    <TableCell style={{ fontSize: '13px' }}>
                      {doc.experienceYears} yrs
                    </TableCell>

                    <TableCell style={{ fontWeight: 600 }}>
                      AED {doc.consultationFee}
                    </TableCell>

                    <TableCell>
                      {doc.isActive ? (
                        <Badge variant="success">Active</Badge>
                      ) : (
                        <Badge variant="neutral">Deactivated</Badge>
                      )}
                    </TableCell>

                    <TableCell style={{ textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', gap: '8px' }}>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => openEditModal(doc)}
                          title="Edit doctor profile"
                        >
                          <Edit2 size={14} />
                        </Button>
                        {doc.isActive && (
                          <Button
                            variant="danger"
                            size="sm"
                            onClick={() => setDeactivatingDoctor(doc)}
                            title="Deactivate doctor"
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

      {/* Create Doctor Modal */}
      <Modal
        isOpen={isCreateModalOpen}
        onClose={closeCreateModal}
        title="Register Doctor & Clinical Profile"
      >
        <form onSubmit={handleSubmitCreate(onCreateSubmit)} style={{ display: 'flex', flexDirection: 'column', gap: '14px', maxHeight: '70vh', overflowY: 'auto', paddingRight: '4px' }}>
          {actionError && (
            <div style={{ padding: '10px 14px', borderRadius: '8px', backgroundColor: '#fef2f2', color: '#991b1b', fontSize: '13px' }}>
              {actionError}
            </div>
          )}

          <div style={{ fontWeight: 600, fontSize: '13px', color: '#0d9488', borderBottom: '1px solid #ccfbf1', paddingBottom: '4px' }}>
            1. DOCTOR User Account & Authentication
          </div>

          <Input
            label="Doctor Full Name"
            placeholder="Dr. John Smith"
            error={errorsCreate.fullName?.message}
            {...registerCreate('fullName')}
          />

          <Input
            label="Staff Email Address"
            type="email"
            placeholder="dr.smith@example.com"
            error={errorsCreate.email?.message}
            {...registerCreate('email')}
          />

          <Input
            label="Phone Number"
            placeholder="+971501234567"
            error={errorsCreate.phone?.message}
            {...registerCreate('phone')}
          />

          <Input
            label="Initial Password (min 8 chars)"
            type="password"
            placeholder="••••••••"
            error={errorsCreate.password?.message}
            {...registerCreate('password')}
          />

          <div style={{ fontWeight: 600, fontSize: '13px', color: '#0d9488', borderBottom: '1px solid #ccfbf1', paddingBottom: '4px', marginTop: '8px' }}>
            2. Professional Profile & Photo
          </div>

          <div>
            <label style={{ fontSize: '13px', fontWeight: 600, color: '#334155', display: 'block', marginBottom: '6px' }}>
              Doctor Photo (Public Profile)
            </label>
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
              {photoUrl ? (
                <img
                  src={photoUrl}
                  alt="Doctor Preview"
                  style={{ width: '48px', height: '48px', borderRadius: '50%', objectFit: 'cover', border: '1px solid #cbd5e1' }}
                />
              ) : (
                <div style={{ width: '48px', height: '48px', borderRadius: '50%', backgroundColor: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#94a3b8' }}>
                  <Upload size={20} />
                </div>
              )}
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={handlePhotoUpload}
                disabled={isUploadingPhoto}
                style={{ fontSize: '13px' }}
              />
              {isUploadingPhoto && <span style={{ fontSize: '12px', color: '#0d9488' }}>Uploading...</span>}
            </div>
          </div>

          <Input
            label="Qualifications"
            placeholder="MBBS, MD, FRCP"
            error={errorsCreate.qualifications?.message}
            {...registerCreate('qualifications')}
          />

          <Input
            label="Languages Spoken"
            placeholder="English, Arabic, Hindi"
            error={errorsCreate.languages?.message}
            {...registerCreate('languages')}
          />

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <Input
              label="Consultation Fee (AED)"
              type="number"
              step="0.01"
              error={errorsCreate.consultationFee?.message}
              {...registerCreate('consultationFee')}
            />

            <Input
              label="Years of Experience"
              type="number"
              error={errorsCreate.experienceYears?.message}
              {...registerCreate('experienceYears')}
            />
          </div>

          <Input
            label="Biography"
            placeholder="Specialist in preventive cardiology..."
            error={errorsCreate.bio?.message}
            {...registerCreate('bio')}
          />

          <div style={{ fontWeight: 600, fontSize: '13px', color: '#0d9488', borderBottom: '1px solid #ccfbf1', paddingBottom: '4px', marginTop: '8px' }}>
            3. Medical Specialties
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            {specialtiesQuery.data?.content.map((spec) => {
              const isSelected = selectedSpecialtyIds.includes(spec.id);
              return (
                <button
                  type="button"
                  key={spec.id}
                  onClick={() => toggleSpecialty(spec.id)}
                  style={{
                    padding: '6px 12px',
                    borderRadius: '6px',
                    border: isSelected ? '1px solid #0d9488' : '1px solid #cbd5e1',
                    backgroundColor: isSelected ? '#ccfbf1' : '#ffffff',
                    color: isSelected ? '#0f766e' : '#334155',
                    fontSize: '12px',
                    fontWeight: isSelected ? 600 : 400,
                    cursor: 'pointer',
                  }}
                >
                  {spec.name}
                </button>
              );
            })}
          </div>

          <div style={{ fontWeight: 600, fontSize: '13px', color: '#0d9488', borderBottom: '1px solid #ccfbf1', paddingBottom: '4px', marginTop: '8px' }}>
            4. Practice Branches
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            {branchesQuery.data?.content.map((branch) => {
              const isSelected = selectedBranchIds.includes(branch.id);
              return (
                <button
                  type="button"
                  key={branch.id}
                  onClick={() => toggleBranch(branch.id)}
                  style={{
                    padding: '6px 12px',
                    borderRadius: '6px',
                    border: isSelected ? '1px solid #0d9488' : '1px solid #cbd5e1',
                    backgroundColor: isSelected ? '#ccfbf1' : '#ffffff',
                    color: isSelected ? '#0f766e' : '#334155',
                    fontSize: '12px',
                    fontWeight: isSelected ? 600 : 400,
                    cursor: 'pointer',
                  }}
                >
                  {branch.name}
                </button>
              );
            })}
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '16px' }}>
            <Button type="button" variant="outline" size="md" onClick={closeCreateModal} disabled={isSubmittingCreate}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="md" disabled={isSubmittingCreate}>
              {isSubmittingCreate ? 'Registering Doctor...' : 'Create Doctor & Profile'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Edit Doctor Modal */}
      <Modal
        isOpen={!!editingDoctor}
        onClose={closeEditModal}
        title={`Edit Profile: Dr. ${editingDoctor?.fullName}`}
      >
        <form onSubmit={handleSubmitEdit(onEditSubmit)} style={{ display: 'flex', flexDirection: 'column', gap: '14px', maxHeight: '70vh', overflowY: 'auto', paddingRight: '4px' }}>
          {actionError && (
            <div style={{ padding: '10px 14px', borderRadius: '8px', backgroundColor: '#fef2f2', color: '#991b1b', fontSize: '13px' }}>
              {actionError}
            </div>
          )}

          <Input
            label="Doctor Full Name"
            error={errorsEdit.fullName?.message}
            {...registerEdit('fullName')}
          />

          <Input
            label="Phone Number"
            error={errorsEdit.phone?.message}
            {...registerEdit('phone')}
          />

          <div>
            <label style={{ fontSize: '13px', fontWeight: 600, color: '#334155', display: 'block', marginBottom: '6px' }}>
              Update Doctor Photo
            </label>
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
              {photoUrl ? (
                <img
                  src={photoUrl}
                  alt="Doctor Preview"
                  style={{ width: '48px', height: '48px', borderRadius: '50%', objectFit: 'cover', border: '1px solid #cbd5e1' }}
                />
              ) : (
                <div style={{ width: '48px', height: '48px', borderRadius: '50%', backgroundColor: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#94a3b8' }}>
                  <Upload size={20} />
                </div>
              )}
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={handlePhotoUpload}
                disabled={isUploadingPhoto}
                style={{ fontSize: '13px' }}
              />
              {isUploadingPhoto && <span style={{ fontSize: '12px', color: '#0d9488' }}>Uploading...</span>}
            </div>
          </div>

          <Input
            label="Qualifications"
            error={errorsEdit.qualifications?.message}
            {...registerEdit('qualifications')}
          />

          <Input
            label="Languages Spoken"
            error={errorsEdit.languages?.message}
            {...registerEdit('languages')}
          />

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <Input
              label="Consultation Fee (AED)"
              type="number"
              step="0.01"
              error={errorsEdit.consultationFee?.message}
              {...registerEdit('consultationFee')}
            />

            <Input
              label="Years of Experience"
              type="number"
              error={errorsEdit.experienceYears?.message}
              {...registerEdit('experienceYears')}
            />
          </div>

          <Input
            label="Biography"
            error={errorsEdit.bio?.message}
            {...registerEdit('bio')}
          />

          <div style={{ fontWeight: 600, fontSize: '13px', color: '#0d9488', borderBottom: '1px solid #ccfbf1', paddingBottom: '4px', marginTop: '8px' }}>
            Assigned Medical Specialties
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            {specialtiesQuery.data?.content.map((spec) => {
              const isSelected = selectedSpecialtyIds.includes(spec.id);
              return (
                <button
                  type="button"
                  key={spec.id}
                  onClick={() => toggleSpecialty(spec.id)}
                  style={{
                    padding: '6px 12px',
                    borderRadius: '6px',
                    border: isSelected ? '1px solid #0d9488' : '1px solid #cbd5e1',
                    backgroundColor: isSelected ? '#ccfbf1' : '#ffffff',
                    color: isSelected ? '#0f766e' : '#334155',
                    fontSize: '12px',
                    fontWeight: isSelected ? 600 : 400,
                    cursor: 'pointer',
                  }}
                >
                  {spec.name}
                </button>
              );
            })}
          </div>

          <div style={{ fontWeight: 600, fontSize: '13px', color: '#0d9488', borderBottom: '1px solid #ccfbf1', paddingBottom: '4px', marginTop: '8px' }}>
            Assigned Practice Branches
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            {branchesQuery.data?.content.map((branch) => {
              const isSelected = selectedBranchIds.includes(branch.id);
              return (
                <button
                  type="button"
                  key={branch.id}
                  onClick={() => toggleBranch(branch.id)}
                  style={{
                    padding: '6px 12px',
                    borderRadius: '6px',
                    border: isSelected ? '1px solid #0d9488' : '1px solid #cbd5e1',
                    backgroundColor: isSelected ? '#ccfbf1' : '#ffffff',
                    color: isSelected ? '#0f766e' : '#334155',
                    fontSize: '12px',
                    fontWeight: isSelected ? 600 : 400,
                    cursor: 'pointer',
                  }}
                >
                  {branch.name}
                </button>
              );
            })}
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '16px' }}>
            <Button type="button" variant="outline" size="md" onClick={closeEditModal} disabled={isSubmittingEdit}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="md" disabled={isSubmittingEdit}>
              {isSubmittingEdit ? 'Saving Changes...' : 'Update Doctor Profile'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Deactivate Confirm Dialog */}
      <ConfirmDialog
        isOpen={!!deactivatingDoctor}
        title="Deactivate Doctor Profile"
        message={`Are you sure you want to deactivate Dr. ${deactivatingDoctor?.fullName}? This action immediately disables their linked user account (${deactivatingDoctor?.email}) and revokes all active refresh tokens.`}
        confirmLabel="Deactivate Doctor"
        variant="danger"
        isLoading={deactivateMutation.isPending}
        onConfirm={() => {
          if (deactivatingDoctor) {
            deactivateMutation.mutate(deactivatingDoctor.id);
          }
        }}
        onCancel={() => setDeactivatingDoctor(null)}
      />
    </div>
  );
};

