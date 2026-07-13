import { http, HttpResponse } from 'msw';

const API_BASE_URL = '/api/v1';

export const handlers = [
  // Mock Auth Login
  http.post(`${API_BASE_URL}/auth/login`, async () => {
    return HttpResponse.json({
      accessToken: 'mock-access-token',
      refreshToken: 'mock-refresh-token',
      email: 'admin@hms.com',
      firstName: 'System',
      lastName: 'Administrator',
      roles: ['ROLE_ADMIN'],
      permissions: ['WRITE_PATIENT', 'DELETE_PATIENT'],
    });
  }),

  // Mock Outpatient Retrieval
  http.get(`${API_BASE_URL}/patients`, () => {
    return HttpResponse.json({
      content: [
        {
          id: 1,
          user: {
            firstName: 'John',
            lastName: 'Doe',
            email: 'john.doe@email.com',
            phone: '5551234567',
          },
          dateOfBirth: '1990-01-01',
          gender: 'MALE',
          bloodGroup: 'O+',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      size: 10,
      number: 0,
    });
  }),
];
