import { apiClient } from './client';

export interface Project {
  id: string;
  external_id: string;
  name: string;
  title: string;
  description: string;
  status: string;
  created_at: string;
  updated_at: string;
}

export interface PaginatedProjects {
  content: Project[];
  pageable: {
    pageNumber: number;
    pageSize: number;
  };
  totalElements: number;
  totalPages: number;
  last: boolean;
  first: boolean;
  size: number;
  number: number;
}

export const projectApi = {
  getMyProjects: async (params: {
    search?: string;
    status?: string;
    page?: number;
    size?: number;
    sortBy?: string;
    sortDesc?: boolean;
  }): Promise<PaginatedProjects> => {
    const { data } = await apiClient.get('/projects/my', { params });
    return data;
  },
};

export default projectApi;
