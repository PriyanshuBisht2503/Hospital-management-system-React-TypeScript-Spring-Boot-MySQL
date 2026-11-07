import { apiRequest } from './client';
import { buildQueryString } from './query';
import type { Branch, PageResponse } from '../types/masterData';

export interface BranchListParams {
  search?: string;
  active?: boolean;
  page?: number;
  size?: number;
}

export function listBranches(params: BranchListParams = {}) {
  return apiRequest<PageResponse<Branch>>(`/api/branches${buildQueryString({ ...params })}`);
}
