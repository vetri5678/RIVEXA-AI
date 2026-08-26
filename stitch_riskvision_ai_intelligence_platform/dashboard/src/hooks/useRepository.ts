import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import repositoryApi from '../api/repository';
import type {
  RepositoryCreateRequest,
  RepositoryUpdateRequest,
  RepositoryFilters,
} from '../types/repository';

import { githubApi } from '../api/githubApi';

const getCurrentUserId = (): string => {
  try {
    const raw = localStorage.getItem('rv_user') || localStorage.getItem('rivexa_user') || localStorage.getItem('user');
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed?.id || parsed?.user_id || parsed?.email) {
        return String(parsed.id || parsed.user_id || parsed.email);
      }
    }
    const token = localStorage.getItem('rv_access_token') || localStorage.getItem('access_token');
    if (token && token.includes('.')) {
      const payload = JSON.parse(atob(token.split('.')[1]));
      if (payload?.userId || payload?.id || payload?.sub) {
        return String(payload.userId || payload.id || payload.sub);
      }
    }
  } catch {}
  return 'unauthenticated';
};

// ─── Query Keys ──────────────────────────────────────────────────────────────

export const REPO_KEYS = {
  all: (userId?: string | null) => ['repositories', userId || 'unauthenticated'] as const,
  lists: (userId?: string | null) => [...REPO_KEYS.all(userId), 'list'] as const,
  list: (filters: Partial<RepositoryFilters>, userId?: string | null) => [...REPO_KEYS.lists(userId), filters] as const,
  connectionStatus: (userId?: string | null) => ['github-connection-status', userId || 'unauthenticated'] as const,
  githubUserRepos: (userId?: string | null) => ['github-repositories', userId || 'unauthenticated'] as const,
  statistics: (userId?: string | null) => [...REPO_KEYS.all(userId), 'statistics'] as const,
  details: (userId?: string | null) => [...REPO_KEYS.all(userId), 'detail'] as const,
  detail: (id: string, userId?: string | null) => [...REPO_KEYS.details(userId), id] as const,
  metrics: (id: string, userId?: string | null) => [...REPO_KEYS.all(userId), 'metrics', id] as const,
  history: (id: string, userId?: string | null) => [...REPO_KEYS.all(userId), 'history', id] as const,
};

// ─── Queries ─────────────────────────────────────────────────────────────────

export const useRepositories = (filters: Partial<RepositoryFilters> = {}) => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: REPO_KEYS.list(filters, userId),
    queryFn: () => repositoryApi.getAll(filters),
    placeholderData: keepPreviousData,
    enabled: userId !== 'unauthenticated',
  });
};

export const useGithubConnectionStatus = () => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: REPO_KEYS.connectionStatus(userId),
    queryFn: githubApi.getConnectionStatus,
    staleTime: 10000,
  });
};

export const useGithubUserRepositories = () => {
  const userId = getCurrentUserId();
  const { data: statusData } = useGithubConnectionStatus();
  const isConnected = statusData?.connected ?? false;

  return useQuery({
    queryKey: REPO_KEYS.githubUserRepos(userId),
    queryFn: async () => {
      const res = await githubApi.getRepositories();
      return res;
    },
    enabled: isConnected,
    staleTime: 30000,
  });
};

export const useDisconnectGithub = () => {
  const queryClient = useQueryClient();
  const userId = getCurrentUserId();
  return useMutation({
    mutationFn: githubApi.disconnectAccount,
    onMutate: async () => {
      // 1. Cancel any outgoing refetches so they don't overwrite our optimistic update
      await queryClient.cancelQueries({ queryKey: REPO_KEYS.connectionStatus(userId) });
      await queryClient.cancelQueries({ queryKey: ['github-connection-status'] });

      // 2. Snapshot the previous connection status for rollback if request fails
      const previousStatus = queryClient.getQueryData(REPO_KEYS.connectionStatus(userId));

      // 3. Optimistically update local query caches immediately
      const disconnectedState = { connected: false, status: 'DISCONNECTED', githubUser: null, repositoryCount: 0 };
      queryClient.setQueryData(REPO_KEYS.connectionStatus(userId), disconnectedState);
      queryClient.setQueriesData({ queryKey: ['github-connection-status'] }, disconnectedState);
      queryClient.setQueriesData({ queryKey: ['github-connection'] }, disconnectedState);
      queryClient.setQueriesData({ queryKey: REPO_KEYS.githubUserRepos(userId) }, { connected: false, repositories: [] });
      queryClient.setQueriesData({ queryKey: ['github-repositories'] }, { connected: false, repositories: [] });

      return { previousStatus };
    },
    onError: (_err, _variables, context) => {
      // Rollback to previous connection state on error
      if (context?.previousStatus) {
        queryClient.setQueryData(REPO_KEYS.connectionStatus(userId), context.previousStatus);
        queryClient.setQueriesData({ queryKey: ['github-connection-status'] }, context.previousStatus);
      }
    },
    onSuccess: () => {
      // 1. Confirm connection status and overview caches as disconnected
      const disconnectedState = { connected: false, status: 'DISCONNECTED', githubUser: null, repositoryCount: 0 };
      queryClient.setQueryData(REPO_KEYS.connectionStatus(userId), disconnectedState);
      queryClient.setQueriesData({ queryKey: ['github-connection-status'] }, disconnectedState);
      queryClient.setQueriesData({ queryKey: ['github-connection'] }, disconnectedState);
      queryClient.setQueriesData({ queryKey: REPO_KEYS.githubUserRepos(userId) }, { connected: false, repositories: [] });
      queryClient.setQueriesData({ queryKey: ['github-repositories'] }, { connected: false, repositories: [] });

      // 2. Remove repository queries
      queryClient.removeQueries({ queryKey: ['github-repositories'] });
      queryClient.removeQueries({ queryKey: REPO_KEYS.githubUserRepos(userId) });

      // 3. Invalidate relevant queries
      queryClient.invalidateQueries({ queryKey: ['github-connection-status'] });
      queryClient.invalidateQueries({ queryKey: ['repositories'] });
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all() });
      queryClient.invalidateQueries({ queryKey: ['overview'] });
      queryClient.invalidateQueries({ queryKey: ['graveyard-index'] });
      queryClient.invalidateQueries({ queryKey: ['org-health'] });
      queryClient.invalidateQueries({ queryKey: ['risk-distribution'] });
      queryClient.invalidateQueries({ queryKey: ['prediction-summary'] });
      queryClient.invalidateQueries({ queryKey: ['high-risk-projects'] });
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['github-connection-status'] });
    },
  });
};

export const useRepositoryStatistics = () => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: REPO_KEYS.statistics(userId),
    queryFn: repositoryApi.getStatistics,
    refetchInterval: 30000, // Refresh KPIs every 30s
    enabled: userId !== 'unauthenticated',
  });
};

export const useRepositoryById = (id: string | null) => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: REPO_KEYS.detail(id!, userId),
    queryFn: () => repositoryApi.getById(id!),
    enabled: !!id && userId !== 'unauthenticated',
  });
};

export const useRepositoryMetrics = (id: string | null) => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: REPO_KEYS.metrics(id!, userId),
    queryFn: () => repositoryApi.getMetrics(id!),
    enabled: !!id && userId !== 'unauthenticated',
  });
};

export const useRepositoryHistory = (id: string | null) => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: REPO_KEYS.history(id!, userId),
    queryFn: () => repositoryApi.getHistory(id!),
    enabled: !!id && userId !== 'unauthenticated',
  });
};

// ─── Mutations ───────────────────────────────────────────────────────────────

export const useCreateRepository = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: RepositoryCreateRequest) => repositoryApi.create(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all() });
    },
  });
};

export const useUpdateRepository = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: RepositoryUpdateRequest }) =>
      repositoryApi.update(id, request),
    onSuccess: (_data, { id }) => {
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all() });
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.detail(id) });
    },
  });
};

export const useDeleteRepository = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => repositoryApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all() });
    },
  });
};

export const useArchiveRepository = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => repositoryApi.archive(id),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all() });
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.detail(id) });
    },
  });
};

export const useRestoreRepository = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => repositoryApi.restore(id),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all() });
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.detail(id) });
    },
  });
};

export const useDuplicateRepository = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => repositoryApi.duplicate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all() });
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
      queryClient.invalidateQueries({ queryKey: REPO_KEYS.all() });
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
    mutationFn: (filters?: Partial<RepositoryFilters>) =>
      repositoryApi.exportCsv(filters),
  });
};

export const useDownloadPdfReport = () => {
  return useMutation({
    mutationFn: (id?: string) => repositoryApi.downloadPdf(id),
  });
};

export const useDownloadExcelReport = () => {
  return useMutation({
    mutationFn: (id?: string) => repositoryApi.downloadExcel(id),
  });
};

export default {
  useRepositories,
  useGithubUserRepositories,
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
  useDownloadPdfReport,
  useDownloadExcelReport,
};
