export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  first: boolean;
  last: boolean;
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
  createdAt: string;
  updatedAt: string;
}

export interface Department {
  id: number;
  code: string;
  name: string;
  description?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Specialty {
  id: number;
  code: string;
  name: string;
  description?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Room {
  id: number;
  branchId: number;
  branchName: string;
  departmentId: number;
  departmentName: string;
  roomNumber: string;
  roomType: string;
  floorNumber: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}
