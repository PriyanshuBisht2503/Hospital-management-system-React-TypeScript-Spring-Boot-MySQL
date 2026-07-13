export interface User {
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
  permissions: string[];
}

export interface Patient {
  id: number;
  user: {
    firstName: string;
    lastName: string;
    email: string;
    phone: string;
  };
  dateOfBirth: string;
  gender: string;
  bloodGroup: string;
  address?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
}

export interface Doctor {
  id: number;
  user: {
    firstName: string;
    lastName: string;
    email: string;
    phone: string;
  };
  specialization: string;
}

export interface Appointment {
  id: number;
  patient: {
    id: number;
    user: {
      firstName: string;
      lastName: string;
    };
  };
  doctor: {
    id: number;
    user: {
      firstName: string;
      lastName: string;
    };
    specialization: string;
  };
  appointmentDate: string;
  timeSlot: string;
  status: string;
  tokenNumber: number;
}

export interface Invoice {
  id: number;
  patient: {
    id: number;
    user: {
      firstName: string;
      lastName: string;
    };
  };
  totalAmount: number;
  taxAmount: number;
  netAmount: number;
  status: string;
  dueDate: string;
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
