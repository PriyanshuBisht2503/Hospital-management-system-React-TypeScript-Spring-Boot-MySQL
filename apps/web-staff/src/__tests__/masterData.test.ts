import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiRequest } from '../api/client';
import { roomSchema, RoomFormData } from '../pages/RoomsPage';
import { ApiError } from '../types/auth';
import { Room, Department, PageResponse } from '../types/masterData';

describe('Master Data Frontend Validation and API Flows', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('Room Form Payload Backend Compatibility', () => {
    it('validates and accepts form payloads matching backend RoomRequest exactly', () => {
      const validPayload: RoomFormData = {
        branchId: 1,
        departmentId: 2,
        roomNumber: 'OR-101',
        type: 'OPERATION_THEATRE',
      };

      const result = roomSchema.safeParse(validPayload);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.branchId).toBe(1);
        expect(result.data.departmentId).toBe(2);
        expect(result.data.roomNumber).toBe('OR-101');
        expect(result.data.type).toBe('OPERATION_THEATRE');
      }
    });

    it('rejects invalid or unsupported room type values', () => {
      const invalidPayload = {
        branchId: 1,
        departmentId: 2,
        roomNumber: '101',
        type: 'INVALID_TYPE',
      };

      const result = roomSchema.safeParse(invalidPayload);
      expect(result.success).toBe(false);
    });

    it('successfully posts a room using the exact backend RoomRequest schema', async () => {
      const payload: RoomFormData = {
        branchId: 1,
        departmentId: 3,
        roomNumber: 'ICU-04',
        type: 'ICU',
      };

      let sentBody: string | null = null;
      let sentHeaders: HeadersInit | undefined = undefined;

      const mockFetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();
        if (url === '/api/rooms' && init?.method === 'POST') {
          sentBody = init.body as string;
          sentHeaders = init.headers;
          const createdRoom: Room = {
            id: 42,
            roomNumber: payload.roomNumber,
            branchId: payload.branchId,
            branchName: 'CareHMS Main Campus',
            departmentId: payload.departmentId,
            departmentName: 'Critical Care',
            type: payload.type,
            isActive: true,
          };
          return new Response(JSON.stringify(createdRoom), {
            status: 201,
            headers: { 'Content-Type': 'application/json' },
          });
        }
        return new Response('Not Found', { status: 404 });
      });

      vi.stubGlobal('fetch', mockFetch);

      const created = await apiRequest<Room>('/api/rooms', {
        method: 'POST',
        body: JSON.stringify(payload),
      });

      expect(created.id).toBe(42);
      expect(created.roomNumber).toBe('ICU-04');
      expect(created.type).toBe('ICU');

      expect(sentBody).not.toBeNull();
      const parsed = JSON.parse(sentBody!);
      // Ensure no frontend-only fields like 'floor' or 'floorNumber' are sent
      expect(parsed).toEqual({
        branchId: 1,
        departmentId: 3,
        roomNumber: 'ICU-04',
        type: 'ICU',
      });
    });
  });

  describe('Master Data Create-and-List Flow', () => {
    it('executes create department mutation followed by list refresh', async () => {
      const existingDepartments: Department[] = [
        { id: 1, code: 'CARDIO', name: 'Cardiology', isActive: true },
      ];

      const mockFetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();

        if (url === '/api/departments' && init?.method === 'POST') {
          const body = JSON.parse(init.body as string);
          const newDept: Department = {
            id: 2,
            code: body.code,
            name: body.name,
            description: body.description,
            isActive: true,
          };
          existingDepartments.push(newDept);
          return new Response(JSON.stringify(newDept), {
            status: 201,
            headers: { 'Content-Type': 'application/json' },
          });
        }

        if (url.startsWith('/api/departments') && (!init?.method || init.method === 'GET')) {
          const pageRes: PageResponse<Department> = {
            content: existingDepartments,
            totalElements: existingDepartments.length,
            totalPages: 1,
            pageNumber: 0,
            pageSize: 6,
            isFirst: true,
            isLast: true,
          };
          return new Response(JSON.stringify(pageRes), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
        }

        return new Response('Not Found', { status: 404 });
      });

      vi.stubGlobal('fetch', mockFetch);

      // 1. Initial list
      const initial = await apiRequest<PageResponse<Department>>('/api/departments?page=0&size=10');
      expect(initial.content.length).toBe(1);

      // 2. Create department
      const created = await apiRequest<Department>('/api/departments', {
        method: 'POST',
        body: JSON.stringify({ code: 'PEDIATRICS', name: 'Pediatrics', description: 'Child health' }),
      });
      expect(created.code).toBe('PEDIATRICS');

      // 3. Refreshed list
      const refreshed = await apiRequest<PageResponse<Department>>('/api/departments?page=0&size=10');
      expect(refreshed.content.length).toBe(2);
      expect(refreshed.content[1].name).toBe('Pediatrics');
    });
  });

  describe('409 Conflict Rejection Handling', () => {
    it('surfaces the 409 Conflict error with backend message when deactivation is rejected', async () => {
      const mockFetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === 'string' ? input : input.toString();

        if (url === '/api/branches/1' && init?.method === 'DELETE') {
          return new Response(
            JSON.stringify({
              status: 409,
              error: 'Conflict',
              message: 'Cannot deactivate branch referenced by active doctors or rooms',
              path: '/api/branches/1',
              timestamp: new Date().toISOString(),
            }),
            {
              status: 409,
              headers: { 'Content-Type': 'application/json' },
            }
          );
        }

        return new Response('Not Found', { status: 404 });
      });

      vi.stubGlobal('fetch', mockFetch);

      let caughtError: ApiError | null = null;
      try {
        await apiRequest<void>('/api/branches/1', { method: 'DELETE' });
      } catch (err: unknown) {
        if (err instanceof ApiError) {
          caughtError = err;
        }
      }

      expect(caughtError).not.toBeNull();
      expect(caughtError?.status).toBe(409);
      expect(caughtError?.errorTitle).toBe('Conflict');
      expect(caughtError?.message).toBe('Cannot deactivate branch referenced by active doctors or rooms');
    });
  });
});

