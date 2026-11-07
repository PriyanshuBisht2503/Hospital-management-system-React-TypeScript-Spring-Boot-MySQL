import type { Branch, Specialty } from './masterData';

/** Public doctor profile (mirrors the backend `DoctorPublicDto`). */
export interface DoctorPublic {
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

/** Public doctor search result (mirrors `DoctorSearchPublicDto`, adds `nextAvailableSlot`). */
export interface DoctorSearchResult extends DoctorPublic {
  nextAvailableSlot: string | null;
}

/** Admin-only doctor record (mirrors the backend `DoctorAdminDto`). */
export interface DoctorAdmin {
  id: number;
  userId: number;
  email: string;
  phone: string | null;
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

/** Available appointment slot (mirrors the backend `AvailableSlotDto`). */
export interface AvailableSlot {
  doctorId: number;
  branchId: number;
  startTime: string;
  endTime: string;
  slotMinutes: number;
}
