import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import dashboardApi from '../api/dashboard';
import repositoryApi from '../api/repository';

// ─── User identity helper ────────────────────────────────────────────────────
// Reads the authenticated user's ID from localStorage so we can namespace
// all query keys by user. This prevents cross-user cache leakage.
const getCurrentUserId = (): string | null => {
  try {
    const raw = localStorage.getItem('rv_user') || localStorage.getItem('rivexa_user') || localStorage.getItem('user');
    if (raw) {
      const parsed = JSON.parse(raw);
      return parsed?.id || parsed?.userId || parsed?.user_id || parsed?.sub || parsed?.email || null;
    }
    // Fallback: try JWT sub claim
    const token = localStorage.getItem('rv_access_token') || localStorage.getItem('access_token') || localStorage.getItem('rivexa_token');
    if (token) {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload?.sub || payload?.userId || payload?.email || null;
    }
  } catch {
    // ignore parse errors
  }
  return null;
};

// ─── System status (not user-scoped — global infra health) ───────────────────

export const useSystemStatus = () => {
  return useQuery({
    queryKey: ['system-status'],
    queryFn: dashboardApi.getSystemStatus,
    refetchInterval: 10000,
  });
};

// ─── User-scoped dashboard queries ───────────────────────────────────────────
// All stat queries are namespaced with the current userId so that data
// is never shared between different authenticated users' cache entries.

export const useOverview = () => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['overview', userId],
    queryFn: dashboardApi.getOverview,
    enabled: !!userId,
  });
};

export const useGraveyardIndex = () => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['graveyard-index', userId],
    queryFn: dashboardApi.getGraveyardIndex,
    enabled: !!userId,
  });
};

export const useOrgHealth = () => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['org-health', userId],
    queryFn: dashboardApi.getOrgHealth,
    enabled: !!userId,
  });
};

export const useRiskDistribution = () => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['risk-distribution', userId],
    queryFn: dashboardApi.getRiskDistribution,
    enabled: !!userId,
  });
};

export const usePredictionSummary = () => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['prediction-summary', userId],
    queryFn: dashboardApi.getPredictionSummary,
    enabled: !!userId,
  });
};

export const useRepositoryRanking = (params: {
  page?: number;
  page_size?: number;
  search?: string;
  risk_level?: string;
  sort_by?: string;
  sort_desc?: boolean;
}) => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['repository-ranking', userId, params],
    queryFn: () => dashboardApi.getRepositoryRanking(params),
    enabled: !!userId,
  });
};

export const useHighRiskProjects = (limit = 10) => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['high-risk-projects', userId, limit],
    queryFn: () => dashboardApi.getHighRiskProjects(limit),
    enabled: !!userId,
  });
};

export const useFeatureImportance = () => {
  return useQuery({
    queryKey: ['feature-importance'],
    queryFn: dashboardApi.getFeatureImportance,
    refetchInterval: 30000,
  });
};

export const usePredictionTimeline = (granularity = 'daily') => {
  return useQuery({
    queryKey: ['prediction-timeline', granularity],
    queryFn: () => dashboardApi.getPredictionTimeline(granularity),
  });
};

export const useRecommendations = () => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['recommendations', userId],
    queryFn: dashboardApi.getRecommendations,
    enabled: !!userId,
  });
};

export const useAlerts = () => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['alerts', userId],
    queryFn: dashboardApi.getAlerts,
    refetchInterval: 15000,
    enabled: !!userId,
  });
};

export const useModelInfo = () => {
  return useQuery({
    queryKey: ['model-info'],
    queryFn: dashboardApi.getModelInfo,
    refetchInterval: 30000,
  });
};

export const useActivity = (limit = 50) => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['activity', userId, limit],
    queryFn: () => dashboardApi.getActivity(limit),
    enabled: !!userId,
  });
};

export const useForecast = () => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['forecast', userId],
    queryFn: dashboardApi.getForecast,
    enabled: !!userId,
  });
};

export const useExecutiveSummary = () => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['executive-summary', userId],
    queryFn: dashboardApi.getExecutiveSummary,
    enabled: !!userId,
  });
};

export const useAIInsights = (limit = 10) => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['ai-insights', userId, limit],
    queryFn: () => dashboardApi.getAIInsights(limit),
    enabled: !!userId,
  });
};

export const usePredictMutation = () => {
  const queryClient = useQueryClient();
  const userId = getCurrentUserId();
  return useMutation({
    mutationFn: dashboardApi.predictProject,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['overview', userId] });
      queryClient.invalidateQueries({ queryKey: ['graveyard-index', userId] });
      queryClient.invalidateQueries({ queryKey: ['org-health', userId] });
      queryClient.invalidateQueries({ queryKey: ['repository-ranking', userId] });
      queryClient.invalidateQueries({ queryKey: ['high-risk-projects', userId] });
      queryClient.invalidateQueries({ queryKey: ['prediction-summary', userId] });
    },
  });
};

/**
 * useRepositoryAssessmentMutation — calls Spring Boot POST /api/v1/repositories/{id}/predict
 * which internally invokes the FastAPI ML pipeline and stores results in Supabase.
 * Accepts a repository UUID string.
 */
export const useRepositoryAssessmentMutation = () => {
  const queryClient = useQueryClient();
  const userId = getCurrentUserId();
  return useMutation({
    mutationFn: (repositoryId: string) => dashboardApi.runRepositoryAssessment(repositoryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['overview', userId] });
      queryClient.invalidateQueries({ queryKey: ['graveyard-index', userId] });
      queryClient.invalidateQueries({ queryKey: ['org-health', userId] });
      queryClient.invalidateQueries({ queryKey: ['repository-ranking', userId] });
      queryClient.invalidateQueries({ queryKey: ['high-risk-projects', userId] });
      queryClient.invalidateQueries({ queryKey: ['prediction-summary', userId] });
    },
  });
};

/**
 * useGitHubUrlPredictionMutation — calls Spring Boot POST /api/v1/repositories/predict-by-url
 * Accepts a raw GitHub URL, resolves/creates the repository, and runs prediction.
 */
export const useGitHubUrlPredictionMutation = () => {
  const queryClient = useQueryClient();
  const userId = getCurrentUserId();
  return useMutation({
    mutationFn: (githubUrl: string) => repositoryApi.predictByGithubUrl(githubUrl),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['overview', userId] });
      queryClient.invalidateQueries({ queryKey: ['graveyard-index', userId] });
      queryClient.invalidateQueries({ queryKey: ['org-health', userId] });
      queryClient.invalidateQueries({ queryKey: ['repository-ranking', userId] });
      queryClient.invalidateQueries({ queryKey: ['repositories', userId] });
      queryClient.invalidateQueries({ queryKey: ['high-risk-projects', userId] });
      queryClient.invalidateQueries({ queryKey: ['prediction-summary', userId] });
    },
  });
};

/**
 * useRunPredictionMutation — calls the dedicated POST /api/v1/predictions/run endpoint.
 * Accepts a repository UUID string and returns a full PredictionResultResponse.
 * Invalidates all dashboard queries on success so the UI refreshes automatically.
 */
export const useRunPredictionMutation = () => {
  const queryClient = useQueryClient();
  const userId = getCurrentUserId();
  return useMutation({
    mutationFn: (repositoryId: string) => dashboardApi.runPrediction(repositoryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['prediction-result'] });
      queryClient.invalidateQueries({ queryKey: ['overview', userId] });
      queryClient.invalidateQueries({ queryKey: ['graveyard-index', userId] });
      queryClient.invalidateQueries({ queryKey: ['org-health', userId] });
      queryClient.invalidateQueries({ queryKey: ['repository-ranking', userId] });
      queryClient.invalidateQueries({ queryKey: ['repositories', userId] });
      queryClient.invalidateQueries({ queryKey: ['high-risk-projects', userId] });
      queryClient.invalidateQueries({ queryKey: ['prediction-summary', userId] });
    },
  });
};

/**
 * usePredictionResult — fetches a stored prediction by its UUID.
 * Calls GET /api/v1/predictions/{id}
 * Enabled only when a predictionId is provided.
 */
export const usePredictionResult = (predictionId: string | null) => {
  return useQuery({
    queryKey: ['prediction-result', predictionId],
    queryFn: () => dashboardApi.getPrediction(predictionId!),
    enabled: !!predictionId,
    staleTime: 0, // Always fetch fresh prediction result for selected repository
    retry: 2,
  });
};

export const useRetrainMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: dashboardApi.retrainModel,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['model-info'] });
      queryClient.invalidateQueries({ queryKey: ['system-status'] });
    },
  });
};

export const useTelemetryAnalysis = () => {
  return useQuery({
    queryKey: ['telemetry-analysis'],
    queryFn: dashboardApi.getTelemetryAnalysis,
    refetchInterval: 60000,
  });
};

export const useExplainEventMutation = () => {
  return useMutation({
    mutationFn: (payload: { action: string; description: string }) =>
      dashboardApi.explainEvent(payload),
  });
};

export const useRepositoryRiskAnalysis = (repoId: string | null) => {
  return useQuery({
    queryKey: ['repository-risk-analysis', repoId],
    queryFn: () => (repoId ? dashboardApi.getRepositoryRiskAnalysis(repoId) : null),
    enabled: !!repoId,
  });
};

export const useAuditLogs = (page = 0, size = 20, options?: { enabled?: boolean }) => {
  return useQuery({
    queryKey: ['audit-logs', page, size],
    queryFn: () => dashboardApi.getAuditLogs(page, size),
    refetchInterval: 5000,
    enabled: options?.enabled ?? true,
  });
};

export const useAuditStatistics = (options?: { enabled?: boolean }) => {
  return useQuery({
    queryKey: ['audit-statistics'],
    queryFn: dashboardApi.getAuditStatistics,
    refetchInterval: 15000,
    enabled: options?.enabled ?? true,
  });
};

export const useTelemetryCurrent = () => {
  return useQuery({
    queryKey: ['telemetry-current'],
    queryFn: dashboardApi.getTelemetryCurrent,
    refetchInterval: 5000,
  });
};

export const useTelemetryHistory = (limit = 50) => {
  return useQuery({
    queryKey: ['telemetry-history', limit],
    queryFn: () => dashboardApi.getTelemetryHistory(limit),
    refetchInterval: 15000,
  });
};

export const useTelemetryStatus = () => {
  return useQuery({
    queryKey: ['telemetry-status'],
    queryFn: dashboardApi.getTelemetryStatus,
    refetchInterval: 10000,
  });
};

export const usePipelineLifecycle = () => {
  return useQuery({
    queryKey: ['pipeline-lifecycle'],
    queryFn: dashboardApi.getPipelineLifecycle,
    refetchInterval: 3000,
  });
};

export const useProjectLifecycleCounts = () => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['project-lifecycle-counts', userId],
    queryFn: dashboardApi.getProjectLifecycleCounts,
    refetchInterval: 10000,
    enabled: !!userId,
  });
};

export const useRiskHeatmap = (params: {
  page?: number;
  page_size?: number;
  search?: string;
  risk_level?: string;
  sort_by?: string;
  sort_desc?: boolean;
} = {}) => {
  const userId = getCurrentUserId();
  return useQuery({
    queryKey: ['risk-heatmap', userId, params],
    queryFn: () => dashboardApi.getRiskHeatmapData(params),
    refetchInterval: (query) => (query.state.error ? false : 15000),
    retry: 1,
    enabled: !!userId,
  });
};

export const usePipelineRepositorySync = () => {
  return useQuery({
    queryKey: ['pipeline-repository-sync'],
    queryFn: dashboardApi.getPipelineRepositorySync,
    refetchInterval: 10000,
  });
};

export const usePipelineExtract = () => {
  return useQuery({
    queryKey: ['pipeline-extract'],
    queryFn: dashboardApi.getPipelineExtract,
    refetchInterval: 10000,
  });
};

export const usePipelineCleanse = () => {
  return useQuery({
    queryKey: ['pipeline-cleanse'],
    queryFn: dashboardApi.getPipelineCleanse,
    refetchInterval: 10000,
  });
};

export const usePipelineModel = () => {
  return useQuery({
    queryKey: ['pipeline-model'],
    queryFn: dashboardApi.getPipelineModel,
    refetchInterval: 10000,
  });
};

export const usePipelineInference = () => {
  return useQuery({
    queryKey: ['pipeline-inference'],
    queryFn: dashboardApi.getPipelineInference,
    refetchInterval: 5000,
  });
};

export const usePipelineShap = () => {
  return useQuery({
    queryKey: ['pipeline-shap'],
    queryFn: dashboardApi.getPipelineShap,
    refetchInterval: 15000,
  });
};

export const useLoginHistory = (page = 0, size = 20, options?: { enabled?: boolean }) => {
  return useQuery({
    queryKey: ['login-history', page, size],
    queryFn: () => dashboardApi.getLoginHistory(page, size),
    refetchInterval: 5000,
    enabled: options?.enabled ?? true,
  });
};

// All hooks above are exported individually as named exports.
// useRunPredictionMutation and usePredictionResult are also exported above.
