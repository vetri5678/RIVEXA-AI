import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import repositoryApi from '../api/repository';
import type {
  RepositoryCreateRequest,
  RepositoryUpdateRequest,
  RepositoryFilters,
} from '../types/repository';

// ─── Query Keys ──────────────────────────────────────────────────────────────

export const REPO_KEYS = {
  all: ['repositories'] as const,
  lists: () => [...REPO_KEYS.all, 'list'] as const,
  list: (filters: Partial<RepositoryFilters>) => [...REPO_KEYS.lists(), filters] as const,
  statistics: () => [...REPO_KEYS.all, 'statistics'] as const,
  details: () => [...REPO_KEYS.all, 'detail'] as const,
  detail: (id: string) => [...REPO_KEYS.details(), id] as const,
  metrics: (id: string) => [...REPO_KEYS.all, 'metrics', id] as const,
  history: (id: string) => [...REPO_KEYS.all, 'history', id] as const,
};

// ─── Queries ─────────────────────────────────────────────────────────────────

export const useRepositories = (filters: Partial<RepositoryFilters> = {}) => {
  return useQuery({
    queryKey: REPO_KEYS.list(filters),
    queryFn: () => repositoryApi.getAll(filters),
    placeholderData: keepPreviousData,
  });
};

export const useRepositoryStatistics = () => {
  return useQuery({
    queryKey: REPO_KEYS.statistics(),
    queryFn: repositoryApi.getStatistics,
    refetchInterval: 30000, // Refresh KPIs every 30s
  });
};

export const useRepositoryById = (id: string | null) => {
  return useQuery({
    queryKey: REPO_KEYS.detail(id!),
    queryFn: () => repositoryApi.getById(id!),
    enabled: !!id,
  });
};

export const useRepositoryMetrics = (id: string | null) => {
  return useQuery({
    queryKey: REPO_KEYS.metrics(id!),
    queryFn: () => repositoryApi.getMetrics(id!),
    enabled: !!id,
  });
};

export const useRepositoryHistory = (id: string | null) => {
  return useQuery({
    queryKey: REPO_KEYS.history(id!),
    queryFn: () => repositoryApi.getHistory(id!),
    enabled: !!id,
  });
};

// ─── Mutations ───────────────────────────────────────────────────────────────

export const useCreateRepository = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: RepositoryCreateRequest) => repositoryApi.create(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all });
    },
  });
};

export const useUpdateRepository = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: RepositoryUpdateRequest }) =>
      repositoryApi.update(id, request),
    onSuccess: (_data, { id }) => {
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all });
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.detail(id) });
    },
  });
};

export const useDeleteRepository = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => repositoryApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all });
    },
  });
};

export const useArchiveRepository = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => repositoryApi.archive(id),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all });
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.detail(id) });
    },
  });
};

export const useRestoreRepository = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => repositoryApi.restore(id),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all });
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.detail(id) });
    },
  });
};

export const useDuplicateRepository = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => repositoryApi.duplicate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all });
    },
  });
};

export const useSyncRepository = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => repositoryApi.sync(id),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.detail(id) });
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.lists() });
    },
  });
};

export const usePredictRepository = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => repositoryApi.predict(id),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all });
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.detail(id) });
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.history(id) });
    },
  });
};

export const useValidateToken = () => {
  return useMutation({
    mutationFn: ({ gitProvider, token, repositoryUrl }: { gitProvider: string; token: string; repositoryUrl: string }) =>
      repositoryApi.validateToken(gitProvider, token, repositoryUrl),
  });
};

export const useExportRepositories = () => {
  return useMutation({
    mutationFn: ({ status, riskLevel }: { status?: string; riskLevel?: string }) =>
      repositoryApi.exportAll(status, riskLevel),
  });
};

export default {
  useRepositories,
  useRepositoryStatistics,
  useRepositoryById,
  useRepositoryMetrics,
  useRepositoryHistory,
  useCreateRepository,
  useUpdateRepository,
  useDeleteRepository,
  useArchiveRepository,
  useRestoreRepository,
  useDuplicateRepository,
  useSyncRepository,
  usePredictRepository,
  useValidateToken,
  useExportRepositories,
};
