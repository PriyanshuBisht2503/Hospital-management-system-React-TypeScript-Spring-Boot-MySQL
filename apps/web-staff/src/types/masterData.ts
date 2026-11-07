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
  code: string;
  name: string;
  city: string;
  address?: string;
  phone?: string;
  email?: string;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface Department {
  id: number;
  code: string;
  name: string;
  description?: string;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface Specialty {
  id: number;
  code: string;
  name: string;
  description?: string;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export type RoomType =
  | 'CONSULTATION'
  | 'OPD'
  | 'PROCEDURE'
  | 'TRIAGE'
  | 'WARD'
  | 'ICU'
  | 'OPERATION_THEATRE';

export interface Room {
  id: number;
  roomNumber: string;
  branchId: number;
  branchName: string;
  departmentId: number;
  departmentName: string;
  type: RoomType;
  isActive: boolean;
}
