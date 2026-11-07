export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isFirst: boolean;
  isLast: boolean;
}

export interface Branch {
  id: number;
  name: string;
  code: string;
  address: string | null;
  city: string;
  phone: string | null;
  email: string | null;
  isActive: boolean;
}

export interface Specialty {
  id: number;
  name: string;
  code: string;
  description: string | null;
  isActive: boolean;
}

export interface Doctor {
  id: number;
  fullName: string;
  bio: string | null;
  qualifications: string | null;
  languages: string | null;
  experienceYears: number;
  photoUrl: string | null;
  consultationFee: number;
  specialties: Specialty[];
  branches: Branch[];
  isActive: boolean;
}

export interface DoctorSearch extends Doctor {
  nextAvailableSlot: string | null;
}

export interface AvailableSlot {
  doctorId: number;
  branchId: number;
  startTime: string;
  endTime: string;
  slotMinutes: number;
}
