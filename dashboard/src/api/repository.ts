import { apiClient } from './client';
import type {
  PagedRepositoryResponse,
  RepositoryDetail,
  RepositoryStatistics,
  RepositoryCreateRequest,
  RepositoryUpdateRequest,
  Repository,
  RepositoryMetrics,
  PredictResponse,
  SyncResponse,
  RepositoryFilters,
} from '../types/repository';

const BASE = '/repositories';

export const repositoryApi = {

  // ─── List / Search / Filter ─────────────────────────────────────────────────
  getAll: async (filters: Partial<RepositoryFilters>): Promise<PagedRepositoryResponse> => {
    const params: Record<string, string | number | undefined> = {
      page: filters.page ?? 0,
      size: filters.size ?? 20,
      sortBy: filters.sortBy ?? 'createdAt',
      sortDir: filters.sortDir ?? 'desc',
    };
    if (filters.search) params.search = filters.search;
    if (filters.status) params.status = filters.status;
    if (filters.riskLevel) params.riskLevel = filters.riskLevel;
    if (filters.predictionStatus) params.predictionStatus = filters.predictionStatus;
    if (filters.gitProvider) params.gitProvider = filters.gitProvider;
    if (filters.language) params.language = filters.language;
    if (filters.organization) params.organization = filters.organization;

    const { data } = await apiClient.get<PagedRepositoryResponse>(BASE, { params });
    return data;
  },

  // ─── Statistics ──────────────────────────────────────────────────────────────
  getStatistics: async (): Promise<RepositoryStatistics> => {
    const { data } = await apiClient.get<RepositoryStatistics>(`${BASE}/statistics`);
    return data;
  },

  // ─── Get by ID ───────────────────────────────────────────────────────────────
  getById: async (id: string): Promise<RepositoryDetail> => {
    const { data } = await apiClient.get<RepositoryDetail>(`${BASE}/${id}`);
    return data;
  },

  // ─── Create ──────────────────────────────────────────────────────────────────
  create: async (request: RepositoryCreateRequest): Promise<Repository> => {
    const { data } = await apiClient.post<Repository>(BASE, request);
    return data;
  },

  // ─── Update ──────────────────────────────────────────────────────────────────
  update: async (id: string, request: RepositoryUpdateRequest): Promise<Repository> => {
    const { data } = await apiClient.put<Repository>(`${BASE}/${id}`, request);
    return data;
  },

  // ─── Delete ──────────────────────────────────────────────────────────────────
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`${BASE}/${id}`);
  },

  // ─── Archive / Restore ───────────────────────────────────────────────────────
  archive: async (id: string): Promise<Repository> => {
    const { data } = await apiClient.patch<Repository>(`${BASE}/${id}/archive`);
    return data;
  },

  restore: async (id: string): Promise<Repository> => {
    const { data } = await apiClient.patch<Repository>(`${BASE}/${id}/restore`);
    return data;
  },

  // ─── Duplicate ────────────────────────────────────────────────────────────────
  duplicate: async (id: string): Promise<Repository> => {
    const { data } = await apiClient.post<Repository>(`${BASE}/${id}/duplicate`);
    return data;
  },

  // ─── Sync ─────────────────────────────────────────────────────────────────────
  sync: async (id: string): Promise<SyncResponse> => {
    const { data } = await apiClient.post<SyncResponse>(`${BASE}/${id}/sync`);
    return data;
  },

  // ─── Predict ──────────────────────────────────────────────────────────────────
  predict: async (id: string): Promise<PredictResponse> => {
    const { data } = await apiClient.post<PredictResponse>(`${BASE}/${id}/predict`);
    return data;
  },

  // ─── Metrics ──────────────────────────────────────────────────────────────────
  getMetrics: async (id: string): Promise<RepositoryMetrics> => {
    const { data } = await apiClient.get<RepositoryMetrics>(`${BASE}/${id}/metrics`);
    return data;
  },

  // ─── History ──────────────────────────────────────────────────────────────────
  getHistory: async (id: string): Promise<RepositoryDetail> => {
    const { data } = await apiClient.get<RepositoryDetail>(`${BASE}/${id}/history`);
    return data;
  },

  // ─── Export ───────────────────────────────────────────────────────────────────
  exportAll: async (status?: string, riskLevel?: string): Promise<PagedRepositoryResponse> => {
    const params: Record<string, string | undefined> = {};
    if (status) params.status = status;
    if (riskLevel) params.riskLevel = riskLevel;
    const { data } = await apiClient.get<PagedRepositoryResponse>(`${BASE}/export`, { params });
    return data;
  },

  // ─── Validate Token (for wizard step 2) ───────────────────────────────────────
  validateToken: async (gitProvider: string, token: string, repositoryUrl: string): Promise<{ valid: boolean; message: string }> => {
    const { data } = await apiClient.post(`${BASE}/validate-token`, { gitProvider, token, repositoryUrl });
    return data;
  },
};

export default repositoryApi;
